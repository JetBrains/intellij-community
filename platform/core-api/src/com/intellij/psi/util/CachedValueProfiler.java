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
    void onFrameEnter(long frameId, long parentId, long time);

    void onFrameExit(long frameId, long time);

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
    final long time = currentTime();
    final long id = ourFrameId.incrementAndGet();
    final Frame parent;

    private final Map<CachedValueProvider.Result<?>, StackTraceElement> places = new HashMap<>();
    private long timeComputed;

    Frame() {
      ThreadContext context = ourContext.get();
      parent = context.topFrame;
      context.topFrame = this;
      if (context.consumer != null) {
        context.consumer.onFrameEnter(id, parent == null ? 0 : parent.id, time);
      }
      ourFrameOverhead.count.incrementAndGet();
      ourFrameOverhead.time.addAndGet(currentTime() - time);
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
      if (context.consumer != null) {
        //report place for aborted computations?
        //StackTraceElement place = timeComputed != 0 ? null : findAnyPlace();
        context.consumer.onFrameExit(id, currentTime());
      }

      if (timeComputed != 0) {
        ourFrameOverhead.time.addAndGet(currentTime() - timeComputed);
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
    if (context.consumer == null) return;

    Frame frame = context.topFrame;
    if (frame == null) return;

    StackTraceElement place = original == null ? findExactPlace() :
                              original instanceof CachedValueProvider.Result ? frame.places.get(original) :
                              original instanceof Function ? findAnyPlace() : null;
    if (place == null) return;

    frame.places.put(result, place);
    ourFrameOverhead.time.addAndGet(currentTime() - time);
  }

  @Nullable
  static ValueTracker onResultReturned(@NotNull Frame frame, @NotNull CachedValueProvider.Result<?> result) {
    long time = currentTime();
    ThreadContext context = ourContext.get();
    if (context.consumer == null) return null;

    StackTraceElement place = frame.places.get(result);
    if (place == null) place = findAnyPlace();

    context.consumer.onValueComputed(frame.id, place, frame.time, time);
    return new ValueTracker(place, time);
  }

  @Nullable
  private static StackTraceElement findExactPlace() {
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
  private static StackTraceElement findAnyPlace() {
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

  private static long currentTime() {
    return System.nanoTime();
  }

  public static final class ValueTracker {
    public final StackTraceElement place;
    public final long time;

    ValueTracker(@NotNull StackTraceElement place, long time) {
      this.place = place;
      this.time = time;
    }

    public void invalidate() {
      long time = currentTime();
      ThreadContext context = ourContext.get();
      if (context.consumer != null) {
        context.consumer.onValueInvalidated(context.topFrame == null ? 0 : context.topFrame.id, place, this.time, time);
      }
      ourTrackerOverhead.time.addAndGet(currentTime() - time);
    }

    public void valueUsed() {
      long time = currentTime();
      ThreadContext context = ourContext.get();
      if (context.consumer != null) {
        context.consumer.onValueUsed(context.topFrame == null ? 0 : context.topFrame.id, place, this.time, time);
      }
      ourTrackerOverhead.count.incrementAndGet();
      ourTrackerOverhead.time.addAndGet(currentTime() - time);
    }
  }

  private static class ThreadContext {
    @Nullable Frame topFrame;
    @Nullable EventConsumer consumer = ourEventConsumer;
  }

  private static class Overhead {
    final AtomicLong time = new AtomicLong();
    final AtomicLong count = new AtomicLong();

    String resetAndReport(String eventName) {
      long delta = this.time.getAndSet(0);
      long events = this.count.getAndSet(0);
      NumberFormat format = NumberFormat.getInstance(Locale.US);
      return format.format(events) + " " + eventName + " calls, " +
             format.format(delta) + " overhead ns (" + format.format(delta / events) + " ns/call)";
    }
  }

}
