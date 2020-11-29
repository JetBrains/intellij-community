// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

@ApiStatus.Internal
public final class CachedValueProfiler {
  private static final Logger LOG = Logger.getInstance(CachedValueProfiler.class);

  public interface EventConsumer {
    void onFrameEnter(long frameId, StackTraceElement place, long parentId, long time);

    void onFrameExit(long frameId, long start, long computed, long time);

    void onValueComputed(long frameId, StackTraceElement place, long start, long time);

    void onValueUsed(long frameId, StackTraceElement place, long start, long time);

    void onValueInvalidated(long frameId, StackTraceElement place, long start, long time);
  }

  private static final ThreadLocal<ThreadContext> ourContext = ThreadLocal.withInitial(() -> new ThreadContext());
  private static final AtomicLong ourFrameId = new AtomicLong();
  private static final Overhead ourFrameOverhead = new Overhead();
  private static final Overhead ourTrackerOverhead = new Overhead();

  private static volatile EventConsumer ourEventConsumer;

  public static boolean isProfiling() {
    return ourEventConsumer != null;
  }

  @Nullable
  public static EventConsumer setEventConsumer(@Nullable EventConsumer eventConsumer) {
    EventConsumer prev = ourEventConsumer;
    ourEventConsumer = eventConsumer;
    if (prev != null) {
      LOG.info(ourFrameOverhead.resetAndReport("doCompute()"));
      LOG.info(ourTrackerOverhead.resetAndReport("getValue()"));
    }
    return prev;
  }

  public static final class Frame implements AutoCloseable {
    final long start = currentTime();
    final long id = ourFrameId.incrementAndGet();
    final Frame parent;

    private final Map<CachedValueProvider.Result<?>, StackTraceElement> places = new HashMap<>();
    private long timeConfigured, timeComputed;

    Frame() {
      ThreadContext context = ourContext.get();
      parent = context.topFrame;
      context.topFrame = this;
      if (context.consumer == null || context.consumer != ourEventConsumer) return;

      StackTraceElement place = findCallerPlace();
      context.consumer.onFrameEnter(id, place, parent == null ? 0 : parent.id, start);
      timeConfigured = currentTime();
      ourFrameOverhead.count.incrementAndGet();
      ourFrameOverhead.overhead.addAndGet(timeConfigured - start);
    }

    @Override
    public void close() {
      ThreadContext context = ourContext.get();
      places.clear();
      if (context.topFrame != this) {
        LOG.warn("unexpected frame: " + (context.topFrame == null ? "null" : context.topFrame.id) + ", expected: " + id , new Throwable());
      }
      context.topFrame = parent;
      if (parent == null) {
        ourContext.remove(); // also releases ThreadContext.consumer reference
      }
      if (context.consumer == null || context.consumer != ourEventConsumer) return;

      context.consumer.onFrameExit(id, start, timeComputed, currentTime());
      long cur = currentTime();
      ourFrameOverhead.total.addAndGet(cur - start);
      if (timeComputed != 0) {
        ourFrameOverhead.overhead.addAndGet(cur - timeComputed);
      }
    }

    @Nullable
    public ValueTracker newValueTracker(@NotNull CachedValueProvider.Result<?> result) {
      timeComputed = currentTime();
      return onResultReturned(this, result);
    }
  }

  @NotNull
  public static Frame newFrame() {
    return new Frame();
  }

  static void onResultCreated(@NotNull CachedValueProvider.Result<?> result, @Nullable Object original) {
    long time = currentTime();
    ThreadContext context = ourContext.get();
    if (context.consumer == null || context.consumer != ourEventConsumer) return;

    Frame frame = context.topFrame;
    if (frame == null) return;

    StackTraceElement place = original == null ? findComputationPlace() :
                              original instanceof CachedValueProvider.Result ? frame.places.get(original) :
                              original instanceof Function ? findCallerPlace() : null;
    if (place == null) return;

    frame.places.put(result, place);
    ourFrameOverhead.overhead.addAndGet(currentTime() - time);
  }

  @Nullable
  static ValueTracker onResultReturned(@NotNull Frame frame, @NotNull CachedValueProvider.Result<?> result) {
    long time = currentTime();
    ThreadContext context = ourContext.get();
    if (context.consumer == null) return null;

    StackTraceElement place = frame.places.get(result);
    if (place == null) place = findCallerPlace();

    context.consumer.onValueComputed(frame.id, place, frame.timeConfigured, time);
    return new ValueTracker(place, time);
  }

  @Nullable
  static StackTraceElement findComputationPlace() {
    StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
    int idx, len;
    for (idx = 2, len = stackTrace.length; idx < len; idx ++) {
      String method = stackTrace[idx].getMethodName();
      String className = stackTrace[idx].getClassName();
      if ("doCompute".equals(method) &&
          (className.endsWith("CachedValueImpl") || className.endsWith("CachedValue")) &&
          (className.startsWith("com.intellij.util.") || className.startsWith("com.intellij.psi."))) {
        break;
      }
    }
    if (idx >= len) return null;
    for (--idx; idx > 0; idx--) {
      String className = stackTrace[idx].getClassName();
      if (className.startsWith("com.intellij.util.CachedValue")) continue;
      if (className.startsWith("com.intellij.psi.util.CachedValue")) continue;
      if (className.startsWith("com.intellij.psi.impl.PsiCachedValue")) continue;
      if (className.startsWith("com.intellij.openapi.util.Recursion")) continue;
      break;

    }
    if (idx > 0) {
      return stackTrace[idx];
    }
    return null;
  }

  @NotNull
  static StackTraceElement findCallerPlace() {
    // todo use StackWalker api
    StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
    for (int idx = 2, len = stackTrace.length; idx < len; idx++) {
      String className = stackTrace[idx].getClassName();
      if (className.startsWith("com.intellij.util.CachedValue")) continue;
      if (className.startsWith("com.intellij.psi.util.CachedValue")) continue;
      if (className.startsWith("com.intellij.psi.impl.PsiCachedValue")) continue;
      if (className.startsWith("com.intellij.openapi.util.Recursion")) continue;
      if (className.startsWith("com.intellij.psi.impl.PsiParameterizedCachedValue")) continue;
      return stackTrace[idx];
    }
    return new StackTraceElement("unknown", "unknown", "", -1);
  }

  static long currentTime() {
    return System.nanoTime();
  }

  public static final class ValueTracker {
    final StackTraceElement place;
    final long start;

    ValueTracker(@NotNull StackTraceElement place, long time) {
      this.place = place;
      this.start = time;
    }

    public void invalidate() {
      long time = currentTime();
      ThreadContext context = ourContext.get();
      if (context.consumer == null || context.consumer != ourEventConsumer) return;
      context.consumer.onValueInvalidated(context.topFrame == null ? 0 : context.topFrame.id, place, start, time);
      ourTrackerOverhead.overhead.addAndGet(currentTime() - time);
    }

    public void valueUsed() {
      long time = currentTime();
      ThreadContext context = ourContext.get();
      if (context.consumer == null || context.consumer != ourEventConsumer) return;
      context.consumer.onValueUsed(context.topFrame == null ? 0 : context.topFrame.id, place, start, time);
      ourTrackerOverhead.count.incrementAndGet();
      ourTrackerOverhead.overhead.addAndGet(currentTime() - time);
    }
  }

  private static class ThreadContext {
    @Nullable Frame topFrame;
    @Nullable EventConsumer consumer = ourEventConsumer;
  }

  private static class Overhead {
    final AtomicLong total = new AtomicLong();
    final AtomicLong overhead = new AtomicLong();
    final AtomicLong count = new AtomicLong();

    String resetAndReport(String eventName) {
      long total = this.total.getAndSet(0);
      long overhead = this.overhead.getAndSet(0);
      long count = this.count.getAndSet(0);
      NumberFormat format = NumberFormat.getInstance(Locale.US);
      return format.format(count) + " " + eventName + " calls, " +
             format.format(overhead) + " overhead ns (" + format.format(overhead / count) + " ns/call" +
             (total == 0 ? "" : ", " + String.format("%.2f", overhead / (double)(total / 100)) + " %") + ")";
    }
  }

}
