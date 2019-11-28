// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Queue;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public final class EventsWatcher implements Disposable {

  @NotNull
  private static final Pattern DESCRIPTION_BY_EVENT = Pattern.compile(
    "(([\\p{L}_$][\\p{L}\\p{N}_$]*\\.)*[\\p{L}_$][\\p{L}\\p{N}_$]*)\\[(?<description>\\w+(,runnable=(?<runnable>[^,]+))?[^]]*)].*"
  );
  @NotNull
  private static final Collector<CharSequence, ?, String> JOINING_COLLECTOR = Collectors.joining("\n");
  private static final long ourStartTimestamp = System.currentTimeMillis();

  @Nullable
  public static EventsWatcher getInstance() {
    Application application = ApplicationManager.getApplication();
    return application != null && !application.isDisposed() && Registry.is("events.watcher.enabled") ?
           ServiceManager.getService(EventsWatcher.class) :
           null;
  }

  @NotNull
  private final Set<Class<?>> myWrappers = new HashSet<>();
  @NotNull
  private final Map<String, InvocationInfo> myDurationsByFqn = new HashMap<>();
  @NotNull
  private final ConcurrentLinkedQueue<String> myRunnables = new ConcurrentLinkedQueue<>();
  @NotNull
  private final ConcurrentMap<Class<? extends AWTEvent>, ConcurrentLinkedQueue<String>> myEventsByClass = new ConcurrentHashMap<>();

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
    getDelay(),
    getDelay(),
    TimeUnit.MILLISECONDS
  );

  @Nullable
  private Object myCurrentInstance = null;

  public void runnableStarted(@NotNull Runnable runnable) {
    Object current = runnable;

    while (current != null) {
      Class<?> originalClass = current.getClass();
      Field field = findTargetField(originalClass);

      if (field != null) {
        myWrappers.add(originalClass);
        current = getValue(current, field);
      }
      else {
        break;
      }
    }

    myCurrentInstance = current != null ? current : runnable;
  }

  public void runnableFinished(long startedAt) {
    String representation = Objects.requireNonNull(myCurrentInstance).getClass().getName();

    myDurationsByFqn.compute(
      representation,
      (ignored, count) -> InvocationInfo.computeNext(count, startedAt)
    );

    myRunnables.offer(String.format("%tc,%s%n", startedAt, representation));
  }

  public void edtEventFinished(@NotNull AWTEvent event,
                               long startedAt) {
    String representation = event.toString();
    Matcher matcher = DESCRIPTION_BY_EVENT.matcher(representation);
    boolean found = matcher.find();

    String description = String.format(
      "%dms %s%n",
      System.currentTimeMillis() - startedAt,
      found ? matcher.group("description") : representation
    );

    Class<? extends AWTEvent> eventClass = event.getClass();
    myEventsByClass.putIfAbsent(eventClass, new ConcurrentLinkedQueue<>());
    myEventsByClass.get(eventClass).offer(description);
  }

  @Override
  public void dispose() {
    myThread.cancel(true);
    myExecutor.shutdownNow();

    appendToLogFile("Timing", joinSorted(myDurationsByFqn));
    appendToLogFile("Wrapper", join(myWrappers));
  }

  private void dumpDescriptions() {
    myEventsByClass.forEach((eventClass, events) -> appendToLogFile(
      eventClass.getSimpleName(),
      join(events)
    ));

    appendToLogFile("Runnable", join(myRunnables));
  }

  private void appendToLogFile(@NotNull String kind, @NotNull String text) {
    if (!(myLogPath.isDirectory() || myLogPath.mkdirs())) return;

    try {
      File logFile = new File(myLogPath, kind + "s.log");
      FileUtil.writeToFile(logFile, text, true);
    }
    catch (IOException ignored) {
    }
  }

  private static long getDelay() {
    return 10000;
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
  private static <T extends Comparable<T>> String joinSorted(@NotNull Map<String, T> map) {
    return map
      .entrySet()
      .stream()
      .sorted(Map.Entry.comparingByValue())
      .map(Objects::toString)
      .collect(JOINING_COLLECTOR);
  }

  @NotNull
  private static String join(@NotNull Set<Class<?>> classes) {
    return classes
      .stream()
      .map(Class::getName)
      .sorted()
      .collect(JOINING_COLLECTOR);
  }

  @NotNull
  private static String join(@NotNull Queue<String> queue) {
    StringBuilder builder = new StringBuilder();
    while (!queue.isEmpty()) {
      builder.append(queue.poll());
    }
    return builder.toString();
  }

  private static final class InvocationInfo implements Comparable<InvocationInfo> {

    private final int myCount;
    private final long myDuration;
    private final double myAverage;

    private InvocationInfo(int count, long duration) {
      myCount = count;
      myDuration = duration;
      myAverage = (double)duration / count;
    }

    @Override
    public int compareTo(@NotNull InvocationInfo info) {
      return Double.compare(info.myAverage, myAverage);
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) return true;
      if (other == null || getClass() != other.getClass()) return false;

      InvocationInfo count = (InvocationInfo)other;
      return myCount == count.myCount &&
             myDuration == count.myDuration;
    }

    @Override
    public int hashCode() {
      return Objects.hash(myCount, myDuration);
    }

    @NotNull
    @Override
    public String toString() {
      return String.format(
        "[average: %.2f; count: %d; duration: %d]",
        myAverage,
        myCount,
        myDuration
      );
    }

    @NotNull
    public static InvocationInfo computeNext(@Nullable InvocationInfo info,
                                             long startedAt) {
      int count = info != null ? info.myCount : 0;
      long duration = info != null ? info.myDuration : 0;

      return new InvocationInfo(
        1 + count,
        (System.currentTimeMillis() - startedAt) + duration
      );
    }
  }
}
