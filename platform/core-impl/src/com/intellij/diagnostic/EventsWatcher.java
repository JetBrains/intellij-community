// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.InvocationEvent;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Queue;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ApiStatus.Experimental
public final class EventsWatcher implements Disposable {

  private static final int PUBLISHER_INITIAL_DELAY = 100;
  private static final int PUBLISHER_PERIOD = 1000;

  @NotNull
  private static final Logger LOG = Logger.getInstance(EventsWatcher.class);
  @NotNull
  private static final Pattern DESCRIPTION_BY_EVENT = Pattern.compile(
    "(([\\p{L}_$][\\p{L}\\p{N}_$]*\\.)*[\\p{L}_$][\\p{L}\\p{N}_$]*)\\[(?<description>\\w+(,runnable=(?<runnable>[^,]+))?[^]]*)].*"
  );
  @NotNull
  private static final Collector<CharSequence, ?, String> JOINING_COLLECTOR = Collectors.joining("\n");

  private static final long ourStartTimestamp = System.currentTimeMillis();
  @NotNull
  private static final NotNullLazyValue<Boolean> ourIsEnabled =
    NotNullLazyValue.createValue(() -> Registry.is("ide.event.queue.dispatch.log.enabled", false));
  @NotNull
  private static final NotNullLazyValue<Field> ourRunnableField =
    NotNullLazyValue.createValue(() -> Objects.requireNonNull(findTargetField(InvocationEvent.class)));

  @Nullable
  public static EventsWatcher getInstance() {
    if (!ourIsEnabled.getValue()) {
      return null;
    }

    Application application = ApplicationManager.getApplication();
    if (application == null || application.isDisposed()) {
      return null;
    }

    if (!application.isDispatchThread()) {
      throw new AssertionError("Do not measure background task running time");
    }

    return ServiceManager.getService(EventsWatcher.class);
  }

  @NotNull
  private final ConcurrentMap<String, RunnablesListener.WrapperDescription> myWrappers = new ConcurrentHashMap<>();
  @NotNull
  private final ConcurrentMap<String, RunnablesListener.InvocationsInfo> myDurationsByFqn = new ConcurrentHashMap<>();
  @NotNull
  private final ConcurrentLinkedQueue<RunnablesListener.InvocationDescription> myRunnables = new ConcurrentLinkedQueue<>();
  @NotNull
  private final ConcurrentMap<Class<? extends AWTEvent>, ConcurrentLinkedQueue<RunnablesListener.InvocationDescription>> myEventsByClass =
    new ConcurrentHashMap<>();

  @NotNull
  private final File myLogPath = new File(
    new File(PathManager.getLogPath(), "edt-log"),
    String.format("%tY%<tm%<td-%<tH%<tM%<tS", ourStartTimestamp)
  );
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

  @NotNull
  private final MessageBus myMessageBus;
  @NotNull
  private final MessageBusConnection myConnection;

  @Nullable
  private Object myCurrentInstance = null;
  @Nullable
  private MatchResult myCurrentResult = null;

  public EventsWatcher(@NotNull MessageBus messageBus) {
    myMessageBus = messageBus;
    myConnection = myMessageBus.connect();

    myConnection.subscribe(
      RunnablesListener.TOPIC,
      new RunnablesListener() {
        @Override
        public void eventsProcessed(@NotNull Class<? extends AWTEvent> eventClass,
                                    @NotNull Collection<InvocationDescription> descriptions) {
          appendToLogFile(eventClass.getSimpleName(), descriptions.stream());
        }

        @Override
        public void runnablesProcessed(@NotNull Collection<InvocationDescription> invocations,
                                       @NotNull Collection<InvocationsInfo> infos,
                                       @NotNull Collection<WrapperDescription> wrappers) {
          appendToLogFile("Runnables", invocations.stream());
        }
      }
    );
  }

  public void logTimeMillis(@NotNull String processId, long startedAt) {
    new InvocationLogger(processId, startedAt)
      .log(() -> null);
  }

  public void logTimeMillis(@NotNull AWTEvent event, long startedAt) {
    if ("LaterInvocator.FlushQueue".equals(findGroupByName("runnable"))) return;

    new InvocationLogger(event, startedAt)
      .log(() -> event instanceof InvocationEvent ?
                 (Runnable)getValue(event, ourRunnableField.getValue()) :
                 null);
  }

  public void runnableStarted(@NotNull Runnable runnable) {
    Object current = runnable;

    while (current != null) {
      Class<?> originalClass = current.getClass();
      Field field = findTargetField(originalClass);

      if (field != null) {
        myWrappers.compute(
          originalClass.getName(),
          RunnablesListener.WrapperDescription::computeNext
        );
        current = getValue(current, field);
      }
      else {
        break;
      }
    }

    myCurrentInstance = current != null ? current : runnable;
  }

  public void runnableFinished(@NotNull Runnable runnable,
                               long startedAt) {
    String fqn = Objects.requireNonNull(myCurrentInstance).getClass().getName();
    myCurrentInstance = null;

    RunnablesListener.InvocationDescription description = new RunnablesListener.InvocationDescription(fqn, startedAt);
    myRunnables.offer(description);
    myDurationsByFqn.compute(
      fqn,
      (ignored, info) -> RunnablesListener.InvocationsInfo.computeNext(fqn, description.getDuration(), info)
    );

    new InvocationLogger(description)
      .log(() -> runnable);
  }

  public void edtEventStarted(@NotNull AWTEvent event) {
    Matcher matcher = DESCRIPTION_BY_EVENT.matcher(event.toString());
    myCurrentResult = matcher.find() ?
                      matcher.toMatchResult() :
                      null;
  }

  public void edtEventFinished(@NotNull AWTEvent event,
                               long startedAt) {
    String representation = findGroupByName("description");
    myCurrentResult = null;

    Class<? extends AWTEvent> eventClass = event.getClass();
    myEventsByClass.putIfAbsent(eventClass, new ConcurrentLinkedQueue<>());
    myEventsByClass.get(eventClass)
      .offer(new RunnablesListener.InvocationDescription(
        representation != null ? representation : event.toString(),
        startedAt
      ));
  }

  @Override
  public void dispose() {
    appendToLogFile("Wrappers", myWrappers);
    appendToLogFile("Timings", myDurationsByFqn);

    myThread.cancel(true);
    myExecutor.shutdownNow();

    myConnection.disconnect();
  }

  @Nullable
  private String findGroupByName(@NotNull String groupName) {
    return myCurrentResult instanceof Matcher ?
           ((Matcher)myCurrentResult).group(groupName) :
           null;
  }

  private void dumpDescriptions() {
    if (myMessageBus.isDisposed()) return;

    RunnablesListener publisher = myMessageBus.syncPublisher(RunnablesListener.TOPIC);
    myEventsByClass.forEach((eventClass, events) ->
                              publisher.eventsProcessed(eventClass, joinPolling(events)));
    publisher.runnablesProcessed(
      joinPolling(myRunnables),
      myDurationsByFqn.values(),
      myWrappers.values()
    );
  }

  private <K, V> void appendToLogFile(@NotNull String kind,
                                      @NotNull Map<K, V> entities) {
    appendToLogFile(kind, entities.values().stream().sorted());
  }

  private <T> void appendToLogFile(@NotNull String kind,
                                   @NotNull Stream<T> lines) {
    if (!(myLogPath.isDirectory() || myLogPath.mkdirs())) return;

    try {
      FileUtil.writeToFile(
        new File(myLogPath, kind + ".log"),
        lines.map(Objects::toString).collect(JOINING_COLLECTOR),
        true
      );
    }
    catch (IOException ignored) {
    }
  }

  @Nullable
  private static Field findTargetField(@NotNull Class<?> originalClass) {
    for (Class<?> currentClass = originalClass;
         currentClass != null;
         currentClass = currentClass.getSuperclass()) {
      for (Field field : currentClass.getDeclaredFields()) {
        if (isInstanceField(field) && isCallableOrRunnable(field)) {
          return field;
        }
      }
    }

    return null;
  }

  private static boolean isInstanceField(@NotNull Field field) {
    return !Modifier.isStatic(field.getModifiers());
  }

  private static boolean isCallableOrRunnable(@NotNull Field field) {
    Class<?> fieldType = field.getType();
    return Runnable.class.isAssignableFrom(fieldType) ||
           Callable.class.isAssignableFrom(fieldType);
  }

  @Nullable
  private static Object getValue(@NotNull Object object,
                                 @NotNull Field field) {
    try {
      field.setAccessible(true);
      return field.get(object);
    }
    catch (IllegalAccessException ignored) {
      return null;
    }
  }

  @NotNull
  private static <T> List<T> joinPolling(@NotNull Queue<T> queue) {
    ArrayList<T> builder = new ArrayList<>();
    while (!queue.isEmpty()) {
      builder.add(queue.poll());
    }
    return Collections.unmodifiableList(builder);
  }

  private static final class InvocationLogger {

    @NotNull
    private final RunnablesListener.InvocationDescription myDescription;

    private InvocationLogger(@NotNull RunnablesListener.InvocationDescription description) {
      myDescription = description;
    }

    private InvocationLogger(@NotNull Object process,
                             long startedAt) {
      this(new RunnablesListener.InvocationDescription(process.toString(), startedAt));
    }

    public void log(@NotNull Supplier<Runnable> lazyRunnable) {
      int threshold = Registry.intValue("ide.event.queue.dispatch.threshold", -1);
      if (threshold < 0 ||
          threshold > myDescription.getDuration()) {
        return; // do not measure a time if the threshold is too small
      }

      Runnable runnable = lazyRunnable.get();
      RunnablesListener.InvocationDescription description = runnable != null ?
                                                            new RunnablesListener.InvocationDescription(
                                                              runnable.toString(),
                                                              myDescription.getStartedAt(),
                                                              myDescription.getFinishedAt()
                                                            ) :
                                                            myDescription;
      LOG.warn(description.toString());

      if (runnable != null) {
        addPluginCost(runnable.getClass(), description.getDuration());
      }
    }

    private static void addPluginCost(@NotNull Class<? extends Runnable> runnableClass,
                                      long duration) {
      ClassLoader loader = runnableClass.getClassLoader();
      String pluginId = loader instanceof PluginClassLoader ?
                        ((PluginClassLoader)loader).getPluginIdString() :
                        PluginManagerCore.CORE_PLUGIN_ID;

      StartUpMeasurer.addPluginCost(
        pluginId,
        "invokeLater",
        TimeUnit.MILLISECONDS.toNanos(duration)
      );
    }
  }
}
