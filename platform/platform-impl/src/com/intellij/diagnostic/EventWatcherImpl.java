// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.application.options.RegistryManager;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.cl.PluginAwareClassLoader;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Queue;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.diagnostic.RunnablesListener.*;
import static com.intellij.util.ReflectionUtil.*;

@ApiStatus.Experimental
@ApiStatus.Internal
final class EventWatcherImpl implements EventWatcher, Disposable {

  private static final int PUBLISHER_DELAY = 1000;

  private static final Logger LOG = Logger.getInstance(EventWatcherImpl.class);
  private static final Pattern DESCRIPTION_BY_EVENT = Pattern.compile(
    "(([\\p{L}_$][\\p{L}\\p{N}_$]*\\.)*[\\p{L}_$][\\p{L}\\p{N}_$]*)\\[(?<description>\\w+(,runnable=(?<runnable>[^,]+))?[^]]*)].*"
  );

  private final ConcurrentMap<String, WrapperDescription> myWrappers = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, InvocationsInfo> myDurationsByFqn = new ConcurrentHashMap<>();
  private final ConcurrentLinkedQueue<InvocationDescription> myRunnables = new ConcurrentLinkedQueue<>();
  private final ConcurrentMap<Class<? extends AWTEvent>, ConcurrentLinkedQueue<InvocationDescription>> myEventsByClass =
    new ConcurrentHashMap<>();

  private final Map<? super Runnable, Long> myCurrentCallablesOrRunnables = new Object2LongOpenHashMap<>();
  private final Map<? super AWTEvent, Long> myCurrentResults = new Object2LongOpenHashMap<>();

  private final @NotNull LogFileWriter myLogFileWriter = new LogFileWriter();
  private final @NotNull RegistryValue myThreshold;
  private final @NotNull ScheduledExecutorService myExecutor;
  private @Nullable ScheduledFuture<?> myFuture;

  EventWatcherImpl() {
    Application application = ApplicationManager.getApplication();
    if (application == null ||
        application.isDisposed() ||
        application.isHeadlessEnvironment()) {
      throw ExtensionNotApplicableException.create();
    }

    application.getMessageBus()
      .connect(this)
      .subscribe(TOPIC, myLogFileWriter);

    myThreshold = application.getService(RegistryManager.class)
      .get("ide.event.queue.dispatch.threshold");

    myExecutor = AppExecutorUtil.createBoundedScheduledExecutorService("EDT Events Logger", 1);
    myFuture = scheduleDumping();
  }

  @Override
  public void logTimeMillis(@NotNull String processId,
                            long startedAt,
                            @NotNull Class<? extends Runnable> runnableClass) {
    InvocationDescription description = new InvocationDescription(processId,
                                                                  startedAt,
                                                                  System.currentTimeMillis());
    logTimeMillis(description, runnableClass);
  }

  @RequiresEdt
  @Override
  public void runnableStarted(@NotNull Runnable runnable, long startedAt) {
    myCurrentCallablesOrRunnables.put(runnable, startedAt);
  }

  @RequiresEdt
  @Override
  public void runnableFinished(@NotNull Runnable runnable, long finishedAt) {
    Class<?> runnableOrCallableClass = getCallableOrRunnableClass(runnable);
    InvocationDescription description = new InvocationDescription(runnableOrCallableClass.getName(),
                                                                  Objects.requireNonNull(myCurrentCallablesOrRunnables.remove(runnable)),
                                                                  finishedAt);

    myRunnables.offer(description);
    myDurationsByFqn.compute(description.getProcessId(),
                             (fqn, info) -> InvocationsInfo.computeNext(fqn, description.getDuration(), info));

    logTimeMillis(description, runnableOrCallableClass);
  }

  @RequiresEdt
  @Override
  public void edtEventStarted(@NotNull AWTEvent event, long startedAt) {
    myCurrentResults.put(event, startedAt);
  }

  @RequiresEdt
  @Override
  public void edtEventFinished(@NotNull AWTEvent event, long finishedAt) {
    InvocationDescription description = new InvocationDescription(toDescription(event.toString()),
                                                                  Objects.requireNonNull(myCurrentResults.remove(event)),
                                                                  finishedAt);

    Class<? extends AWTEvent> eventClass = event.getClass();
    myEventsByClass.putIfAbsent(eventClass, new ConcurrentLinkedQueue<>());
    myEventsByClass.get(eventClass).offer(description);
  }

  @Override
  public void reset() {
    myWrappers.clear();
    myDurationsByFqn.clear();
    myRunnables.clear();
    myEventsByClass.clear();

    reschedule(scheduleDumping());
  }

  @Override
  public void dispose() {
    myLogFileWriter.dump();

    reschedule(null);
    myExecutor.shutdownNow();
  }

  private void reschedule(@Nullable ScheduledFuture<?> future) {
    if (myFuture != null) {
      myFuture.cancel(true);
    }
    myFuture = future;
  }

  private @NotNull ScheduledFuture<?> scheduleDumping() {
    return myExecutor.scheduleWithFixedDelay(() -> {
                                               Application application = ApplicationManager.getApplication();
                                               if (application != null && !application.isDisposed()) {
                                                 dumpDescriptions(application.getMessageBus().syncPublisher(TOPIC));
                                               }
                                               else {
                                                 reschedule(null);
                                               }
                                             },
                                             PUBLISHER_DELAY,
                                             PUBLISHER_DELAY,
                                             TimeUnit.MILLISECONDS);
  }

  private void dumpDescriptions(@NotNull RunnablesListener publisher) {
    myEventsByClass.forEach((eventClass, events) ->
                              publisher.eventsProcessed(eventClass, joinPolling(events)));
    publisher.runnablesProcessed(joinPolling(myRunnables),
                                 myDurationsByFqn.values(),
                                 myWrappers.values());
  }

  private @NotNull Class<?> getCallableOrRunnableClass(@NotNull Runnable runnable) {
    Object current = runnable;
    while (current != null) {
      Class<?> rootClass = current.getClass();
      Field targetField = findFieldInHierarchy(rootClass,
                                               field -> isInstanceField(field) && isCallableOrRunnable(field));

      if (targetField != null) {
        myWrappers.compute(rootClass.getName(),
                           WrapperDescription::computeNext);
        current = getFieldValue(targetField, current);
      }
      else {
        break;
      }
    }

    return (current != null ? current : runnable).getClass();
  }

  private static boolean isCallableOrRunnable(@NotNull Field field) {
    Class<?> fieldType = field.getType();
    return isAssignable(Runnable.class, fieldType) ||
           isAssignable(Callable.class, fieldType);
  }

  private static @NotNull <T> List<T> joinPolling(@NotNull Queue<? extends T> queue) {
    ArrayList<T> builder = new ArrayList<>();
    while (!queue.isEmpty()) {
      builder.add(queue.poll());
    }
    return Collections.unmodifiableList(builder);
  }

  private void logTimeMillis(@NotNull InvocationDescription description,
                             @NotNull Class<?> runnableClass) {
    int threshold = myThreshold.asInteger();
    if (threshold < 0 ||
        threshold > description.getDuration()) {
      return; // do not measure a time if the threshold is too small
    }

    LOG.info(description.toString());

    if (runnableClass != Runnable.class) {
      addPluginCost(runnableClass, description.getDuration());
    }
  }

  private static void addPluginCost(@NotNull Class<?> runnableClass,
                                    long duration) {
    ClassLoader loader = runnableClass.getClassLoader();
    String pluginId = loader instanceof PluginAwareClassLoader ?
                      ((PluginAwareClassLoader)loader).getPluginId().getIdString() :
                      PluginManagerCore.CORE_PLUGIN_ID;

    StartUpMeasurer.addPluginCost(pluginId,
                                  "invokeLater",
                                  TimeUnit.MILLISECONDS.toNanos(duration));
  }

  private static @NotNull String toDescription(@NotNull String string) {
    Matcher matcher = DESCRIPTION_BY_EVENT.matcher(string);
    MatchResult matchResult = matcher.find() ?
                              matcher.toMatchResult() :
                              null;
    return matchResult instanceof Matcher ?
           ((Matcher)matchResult).group("description") :
           string;
  }

  private static final class LogFileWriter implements RunnablesListener {

    private final File myLogDir = new File(new File(PathManager.getLogPath(), "edt-log"),
                                           new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date(System.currentTimeMillis())));

    private final ArrayList<InvocationsInfo> myInfos = new ArrayList<>();
    private final ArrayList<WrapperDescription> myWrappers = new ArrayList<>();

    @Override
    public void eventsProcessed(@NotNull Class<? extends AWTEvent> eventClass,
                                @NotNull Collection<InvocationDescription> descriptions) {
      appendToFile(eventClass.getSimpleName(), descriptions);
    }

    @Override
    public void runnablesProcessed(@NotNull Collection<InvocationDescription> invocations,
                                   @NotNull Collection<InvocationsInfo> infos,
                                   @NotNull Collection<WrapperDescription> wrappers) {
      appendToFile("Runnables", invocations);
      myInfos.addAll(infos);
      myWrappers.addAll(wrappers);
    }

    private void dump() {
      sortAndDumpToFile("Timings", myInfos);
      sortAndDumpToFile("Wrappers", myWrappers);
    }

    private <T> void appendToFile(@NotNull String fileName,
                                  @NotNull Collection<? extends T> entities) {
      writeToFile(fileName, entities, true);
    }

    private <T> void writeToFile(@NotNull String fileName,
                                 @NotNull Collection<? extends T> entities,
                                 boolean append) {
      if (!(myLogDir.isDirectory() || myLogDir.mkdirs())) {
        LOG.debug(myLogDir.getAbsolutePath() + " cannot be created");
        return;
      }

      try {
        FileUtil.writeToFile(new File(myLogDir, fileName + ".log"),
                             StringUtil.join(entities, Objects::toString, "\n"),
                             append);
      }
      catch (IOException e) {
        LOG.debug(e);
      }
    }

    private <T extends Comparable<? super T>> void sortAndDumpToFile(@NotNull String fileName,
                                                                     @NotNull List<? extends T> entities) {
      Collections.sort(entities);
      writeToFile(fileName, entities, false);
    }
  }
}
