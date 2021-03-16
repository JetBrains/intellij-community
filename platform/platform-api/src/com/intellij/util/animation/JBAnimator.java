// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.animation;

import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.EdtExecutorService;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * <p>Animator schedules and runs animations.</p>
 *
 * <p>The total duration of one call of {@link #animate(Collection)} depends on
 * summary duration of all submitted animations.</p>
 *
 * <p>Let's assume that next animations were submitted:</p>
 *
 * <pre>
 * a1[delay = 10, duration = 40]
 * a2[delay = 50, duration = 20]
 * a3[delay = 40, duration = 50]
 * </pre>
 *
 * <p>The total duration time is: <code>maxOf(10 + 40, 50 + 20, 40 + 50) = 90</code>,
 * the delay before running first animation cycle is <code>minOf(10, 50, 40) = 10</code>.</p>
 *
 * <p>There're no guarantees that any animation starts exactly in time,
 * but it runs as soon as possible. For example when the period is set to 15 and total delay is 0
 * then an animation with delay = 20 will be played at 30 (on the second cycle).
 * </p>
 *
 * <p>
 *   The simplest way to run an animation:
 *
 *   <pre>
 *     new JBAnimator().animate(
 *       new Animation((v) -> System.out.println(v))
 *     );
 *   </pre>
 * </p>
 *
 * <p>
 *   By default it runs on EDT with duration 500 ms.
 * </p>
 *
 * @see Animation
 * @see Animations
 */
@ApiStatus.Experimental
public final class JBAnimator implements Disposable {

  private int myPeriod = 16;
  private @NotNull Type myType = Type.IN_TIME;
  private boolean myCyclic = false;
  private boolean myIgnorePowerSaveMode = false;

  private final @NotNull ScheduledExecutorService myService;
  private final @NotNull AtomicLong myRunning = new AtomicLong();
  private final @NotNull AtomicBoolean myDisposed = new AtomicBoolean();

  public JBAnimator() {
    this(Thread.SWING_THREAD, null);
  }

  @SuppressWarnings("unused")
  public JBAnimator(@NotNull Disposable parentDisposable) {
    this(Thread.SWING_THREAD, parentDisposable);
  }

  public JBAnimator(@NotNull Thread threadToUse, @Nullable Disposable parentDisposable) {
    myService = threadToUse == Thread.SWING_THREAD ?
                EdtExecutorService.getScheduledExecutorInstance() :
                AppExecutorUtil.createBoundedScheduledExecutorService("Animator Pool", 1);
    if (parentDisposable == null) {
      if (threadToUse != Thread.SWING_THREAD) {
        Logger.getInstance(JBAnimator.class).error(new IllegalArgumentException("You must provide parent Disposable for non-swing thread Alarm"));
      }
    }
    else {
      Disposer.register(parentDisposable, this);
    }
  }

  /**
   * @see #animate(Collection)
   */
  public void animate(Animation @NotNull... animations) {
    animate(Arrays.asList(animations));
  }

  /**
   * Runs collection of animations.
   *
   * If animator is running then stop previous submitted animations
   * and schedule new.
   *
   * @param animations Collection of animations to be scheduled for running.
   */
  public void animate(@NotNull Collection<@NotNull Animation> animations) {
    if (myDisposed.get()) {
      Logger.getInstance(JBAnimator.class).warn("Animator is already disposed");
      return;
    }

    var from = Integer.MAX_VALUE;
    var to = 0;

    for (Animation animation : animations) {
      from = Math.min(animation.getDelay(), from);
      to = Math.max(animation.getDelay() + animation.getDuration(), to);
    }

    final var delay = animations.isEmpty() ? 0 : from;
    final var duration = animations.isEmpty() ? 0 : to - from;

    if (!myIgnorePowerSaveMode && PowerSaveMode.isEnabled() || duration == 0) {
      myService.schedule(() -> {
        myRunning.incrementAndGet();
        for (Animation animation : animations) {
          try {
            animation.fireEvent(Animation.Phase.SCHEDULED);
            animation.update(1.0);
            animation.fireEvent(Animation.Phase.UPDATED);
            animation.fireEvent(Animation.Phase.EXPIRED);
          }
          catch (Throwable t) {
            Logger.getInstance(Animation.class).error(t);
          }
        }
      }, delay, TimeUnit.MILLISECONDS);
      return;
    }

    myService.schedule(new Runnable() {
      final long rid = myRunning.incrementAndGet();
      @Nullable FrameCounter frameCounter;
      @NotNull LinkedHashSet<Animation> scheduledAnimations = new LinkedHashSet<>();

      private void prepareAnimations() {
        frameCounter = create(myType, myPeriod, duration, delay);
        scheduledAnimations = new LinkedHashSet<>(animations);
        for (Animation animation : scheduledAnimations) {
          animation.fireEvent(Animation.Phase.SCHEDULED);
        }
      }

      @Override
      public void run() {
        if (rid < myRunning.get()) return;
        if (frameCounter == null) {
          prepareAnimations();
        }
        long totalFrames = frameCounter.getTotalFrames();
        long currentFrame = Math.min(frameCounter.getCurrentFrame(), totalFrames);
        long nextDelay = frameCounter.getDelay(currentFrame);
        double timeline = (double) currentFrame / totalFrames;
        if (currentFrame >= totalFrames && myCyclic) {
          frameCounter = null;
        }
        final var expired = new LinkedList<Animation>();
        for (Animation animation : scheduledAnimations) {
          double start = (double) (animation.getDelay() - delay) / duration;
          double end = start + (double) animation.getDuration() / duration;
          if (start <= timeline) try {
            animation.update((timeline - start) / (end - start));
            animation.fireEvent(Animation.Phase.UPDATED);
          }
          catch (Throwable t) {
            Logger.getInstance(Animation.class).error(t);
          }
          if (timeline > end) {
            expired.add(animation);
          }
        }
        expired.forEach(scheduledAnimations::remove);
        boolean isProceed = currentFrame < totalFrames || myCyclic;
        for (Animation animation : isProceed ? expired : scheduledAnimations) {
          animation.fireEvent(Animation.Phase.EXPIRED);
        }
        if (isProceed) {
          myService.schedule(this, nextDelay, TimeUnit.MILLISECONDS);
        }
      }
    }, delay, TimeUnit.MILLISECONDS);
  }

  public void stop() {
    myRunning.incrementAndGet();
  }

  public int getPeriod() {
    return myPeriod;
  }

  public @NotNull JBAnimator setPeriod(int period) {
    myPeriod = Math.max(period, 5);
    return this;
  }

  public boolean isCyclic() {
    return myCyclic;
  }

  public @NotNull JBAnimator setCyclic(boolean cyclic) {
    myCyclic = cyclic;
    return this;
  }

  public @NotNull JBAnimator ignorePowerSaveMode() {
    myIgnorePowerSaveMode = true;
    return this;
  }

  public @NotNull Type getType() {
    return myType;
  }

  public @NotNull JBAnimator setType(Type type) {
    myType = type;
    return this;
  }

  @Override
  public void dispose() {
    stop();

    if (!myDisposed.getAndSet(true) && myService != EdtExecutorService.getScheduledExecutorInstance()) {
      myService.shutdownNow();
    }
  }

  /**
   * <p>The thread where {@link Animation#update(double)} and {@link Animation.Listener#update(Animation.Phase)} will be called.</p>
   */
  public enum Thread {
    SWING_THREAD, POOLED_THREAD
  }

  /**
   * <p>Animation can be played 2 different ways:</p>
   *
   * <ul>
   *   <li>Every frame of the animation will be played but total duration of the animation can be longer.</li>
   *   <li>Some frames of animation can be missed but total duration will be close to demanded.</li>
   * </ul>
   */
  public enum Type {
    EACH_FRAME, IN_TIME,
  }

  private interface FrameCounter {
    long getCurrentFrame();
    long getTotalFrames();
    long getDelay(long currentFrame);
  }

  private static FrameCounter create(@NotNull Type type, int period, int duration, int delay) {
    switch (type) {
      case EACH_FRAME:
        return new FrameCounter() {

          final long frames = duration / period + ((duration % period == 0) ? 0 : 1);
          long frame = 0;

          @Override
          public long getCurrentFrame() {
            return frame++;
          }

          @Override
          public long getTotalFrames() {
            // at least one frame should be played
            return Math.max(frames, 1);
          }

          @Override
          public long getDelay(long currentFrame) {
            return period;
          }
        };

      case IN_TIME:
        return new FrameCounter() {

          final long startTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime()) + delay;

          @Override
          public long getCurrentFrame() {
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime()) - startTime;
          }

          @Override
          public long getTotalFrames() {
            return duration;
          }

          @Override
          public long getDelay(long currentFrame) {
            return Math.min(duration - currentFrame, period);
          }
        };
      default:
        throw new AssertionError();
    }
  }
}
