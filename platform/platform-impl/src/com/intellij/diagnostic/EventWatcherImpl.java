// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Queue;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.diagnostic.RunnablesListener.*;
import static com.intellij.util.ReflectionUtil.*;

@ApiStatus.Experimental
public final class EventWatcherImpl implements EventWatcher, Disposable {
  private static final int PUBLISHER_INITIAL_DELAY = 100;
  private static final int PUBLISHER_PERIOD = 1000;

  @NotNull
  private static final Logger LOG = Logger.getInstance(EventWatcherImpl.class);
  @NotNull
  private static final Pattern DESCRIPTION_BY_EVENT = Pattern.compile(
    "(([\\p{L}_$][\\p{L}\\p{N}_$]*\\.)*[\\p{L}_$][\\p{L}\\p{N}_$]*)\\[(?<description>\\w+(,runnable=(?<runnable>[^,]+))?[^]]*)].*"
  );

  @NotNull
  private final ConcurrentMap<String, WrapperDescription> myWrappers = new ConcurrentHashMap<>();
  @NotNull
  private final ConcurrentMap<String, InvocationsInfo> myDurationsByFqn = new ConcurrentHashMap<>();
  @NotNull
  private final ConcurrentLinkedQueue<InvocationDescription> myRunnables = new ConcurrentLinkedQueue<>();
  @NotNull
  private final ConcurrentMap<Class<? extends AWTEvent>, ConcurrentLinkedQueue<InvocationDescription>> myEventsByClass =
    new ConcurrentHashMap<>();
  private final @NotNull ConcurrentMap<Long, Class<?>> myRunnablesOrCallablesInProgress = new ConcurrentHashMap<>();
  private final @NotNull ConcurrentMap<String, LockAcquirementDescription> myAcquirements = new ConcurrentHashMap<>();

  @NotNull
  private final ScheduledExecutorService myExecutor = AppExecutorUtil.createBoundedScheduledExecutorService(
    "EDT Events Logger",
    1
  );
  @NotNull
  private final ScheduledFuture<?> myThread = myExecutor.scheduleWithFixedDelay(
    this::dumpDescriptions,
    PUBLISHER_INITIAL_DELAY,
    PUBLISHER_PERIOD,
    TimeUnit.MILLISECONDS
  );

  private final @NotNull LogFileWriter myWriter = new LogFileWriter();
  private final @NotNull MessageBus myMessageBus;

  @Nullable
  private MatchResult myCurrentResult = null;

  public EventWatcherImpl(@NotNull MessageBus messageBus) {
    myMessageBus = messageBus;
    myMessageBus.connect(this).subscribe(TOPIC, myWriter);
  }

  @Override
  public void logTimeMillis(@NotNull String processId, long startedAt,
                            @NotNull Class<? extends Runnable> runnableClass) {
    InvocationDescription description = new InvocationDescription(processId, startedAt);
    logTimeMillis(description, runnableClass);
  }

  @Override
  public void runnableStarted(@NotNull Runnable runnable, long startedAt) {
    Object current = runnable;

    while (current != null) {
      Class<?> rootClass = current.getClass();
      Field field = findCallableOrRunnableField(rootClass);

      if (field != null) {
        myWrappers.compute(
          rootClass.getName(),
          WrapperDescription::computeNext
        );
        current = getFieldValue(field, current);
      }
      else {
        break;
      }
    }

    myRunnablesOrCallablesInProgress.put(
      startedAt,
      (current != null ? current : runnable).getClass()
    );
  }

  @Override
  public void runnableFinished(@NotNull Runnable runnable, long startedAt) {
    Class<?> runnableOrCallableClass = Objects.requireNonNull(myRunnablesOrCallablesInProgress.remove(startedAt));
    String fqn = runnableOrCallableClass.getName();

    InvocationDescription description = new InvocationDescription(fqn, startedAt);
    myRunnables.offer(description);
    myDurationsByFqn.compute(
      fqn,
      (ignored, info) -> InvocationsInfo.computeNext(fqn, description.getDuration(), info)
    );

    logTimeMillis(description, runnableOrCallableClass);
  }

  @Override
  public void edtEventStarted(@NotNull AWTEvent event) {
    Matcher matcher = DESCRIPTION_BY_EVENT.matcher(event.toString());
    myCurrentResult = matcher.find() ?
                      matcher.toMatchResult() :
                      null;
  }

  @Override
  public void edtEventFinished(@NotNull AWTEvent event, long startedAt) {
    String representation = myCurrentResult instanceof Matcher ?
                            ((Matcher)myCurrentResult).group("description") :
                            event.toString();
    myCurrentResult = null;

    Class<? extends AWTEvent> eventClass = event.getClass();
    myEventsByClass.putIfAbsent(eventClass, new ConcurrentLinkedQueue<>());
    myEventsByClass.get(eventClass)
      .offer(new InvocationDescription(representation, startedAt));
  }

  @Override
  public void lockAcquired(@NotNull String invokedClassFqn, @NotNull LockKind lockKind) {
    myAcquirements.compute(
      invokedClassFqn,
      (fqn, description) -> LockAcquirementDescription.computeNext(fqn, description, lockKind)
    );
  }

  @Override
  public void dispose() {
    Disposer.dispose(myWriter);

    myThread.cancel(true);
    myExecutor.shutdownNow();
  }

  private void dumpDescriptions() {
    if (myMessageBus.isDisposed()) return;

    RunnablesListener publisher = myMessageBus.syncPublisher(TOPIC);
    myEventsByClass.forEach((eventClass, events) ->
                              publisher.eventsProcessed(eventClass, joinPolling(events)));
    publisher.runnablesProcessed(
      joinPolling(myRunnables),
      myDurationsByFqn.values(),
      myWrappers.values()
    );
    publisher.locksAcquired(myAcquirements.values());
  }

  private static @Nullable Field findCallableOrRunnableField(@NotNull Class<?> rootClass) {
    return findFieldInHierarchy(
      rootClass,
      field -> isInstanceField(field) && isCallableOrRunnable(field)
    );
  }

  private static boolean isCallableOrRunnable(@NotNull Field field) {
    Class<?> fieldType = field.getType();
    return isAssignable(Runnable.class, fieldType) ||
           isAssignable(Callable.class, fieldType);
  }

  @NotNull
  private static <T> List<T> joinPolling(@NotNull Queue<? extends T> queue) {
    ArrayList<T> builder = new ArrayList<>();
    while (!queue.isEmpty()) {
      builder.add(queue.poll());
    }
    return Collections.unmodifiableList(builder);
  }

  private static void logTimeMillis(@NotNull InvocationDescription description,
                                    @NotNull Class<?> runnableClass) {
    LoadingState.CONFIGURATION_STORE_INITIALIZED.checkOccurred();

    int threshold = Registry.intValue("ide.event.queue.dispatch.threshold", -1);
    if (threshold < 0 ||
        threshold > description.getDuration()) {
      return; // do not measure a time if the threshold is too small
    }

    LOG.warn(description.toString());

    if (runnableClass != Runnable.class) {
      addPluginCost(runnableClass, description.getDuration());
    }
  }

  private static void addPluginCost(@NotNull Class<?> runnableClass,
                                    long duration) {
    ClassLoader loader = runnableClass.getClassLoader();
    String pluginId = loader instanceof PluginClassLoader ?
                      ((PluginClassLoader)loader).getPluginId().getIdString() :
                      PluginManagerCore.CORE_PLUGIN_ID;

    StartUpMeasurer.addPluginCost(
      pluginId,
      "invokeLater",
      TimeUnit.MILLISECONDS.toNanos(duration)
    );
  }

  private static final class LogFileWriter implements RunnablesListener, Disposable {

    private final @NotNull File myLogDir = new File(
      new File(PathManager.getLogPath(), "edt-log"),
      String.format("%tY%<tm%<td-%<tH%<tM%<tS", System.currentTimeMillis())
    );

    private final @NotNull Map<String, InvocationsInfo> myInfos = new HashMap<>();
    private final @NotNull Map<String, WrapperDescription> myWrappers = new HashMap<>();

    @Override
    public void eventsProcessed(@NotNull Class<? extends AWTEvent> eventClass,
                                @NotNull Collection<InvocationDescription> descriptions) {
      appendToFile(eventClass.getSimpleName(), descriptions.stream());
    }

    @Override
    public void runnablesProcessed(@NotNull Collection<InvocationDescription> invocations,
                                   @NotNull Collection<InvocationsInfo> infos,
                                   @NotNull Collection<WrapperDescription> wrappers) {
      appendToFile("Runnables", invocations.stream());

      putAllTo(infos, InvocationsInfo::getFQN, myInfos);
      putAllTo(wrappers, WrapperDescription::getFQN, myWrappers);
    }

    @Override
    public void dispose() {
      writeToFile("Timings", myInfos);
      writeToFile("Wrappers", myWrappers);
    }

    private <T> void appendToFile(@NotNull String kind,
                                  @NotNull Stream<T> lines) {
      if (!(myLogDir.isDirectory() || myLogDir.mkdirs())) {
        LOG.debug(myLogDir.getAbsolutePath() + " cannot be created");
        return;
      }

      try {
        FileUtil.writeToFile(
          new File(myLogDir, kind + ".log"),
          lines.map(Objects::toString).collect(Collectors.joining("\n")),
          true
        );
      }
      catch (IOException e) {
        LOG.debug(e);
      }
    }

    private <K, V> void writeToFile(@NotNull String kind,
                                    @NotNull Map<K, V> entities) {
      appendToFile(kind, entities.values().stream().sorted());
    }

    private static <E> void putAllTo(@NotNull Collection<? extends E> entities,
                                     @NotNull Function<? super E, String> mapper,
                                     @NotNull Map<String, E> map) {
      Map<String, E> entitiesMap = entities
        .stream()
        .collect(Collectors.toMap(mapper, Function.identity()));
      map.putAll(entitiesMap);
    }
  }
}
