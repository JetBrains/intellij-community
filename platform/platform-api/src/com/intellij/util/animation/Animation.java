// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.animation;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/**
 * <p>Animation updates anything when {@link JBAnimator} runs.</p>
 *
 * <p>Holds 3 main options:</p>
 * <ul>
 * <li>duration</li>
 * <li>easing</li>
 * <li>consumer</li>
 * </ul>
 *
 * <p>Delay and listeners are optional.</p>
 *
 * @see JBAnimator
 * @see Easing
 */
@ApiStatus.Experimental
public final class Animation {

  private @NotNull final DoubleConsumer myConsumer;
  private @NotNull Easing myEasing = Easing.EASE;
  private int myDelay = 0;
  private int myDuration = 500;
  private @Nullable List<Listener> myListeners;

  public Animation(@NotNull DoubleConsumer consumer) {
    myConsumer = consumer;
  }

  public Animation(DoubleConsumer @NotNull ... consumers) {
    myConsumer = value -> {
      for (DoubleConsumer consumer : consumers) {
        consumer.accept(value);
      }
    };
  }

  public <T> Animation(@NotNull DoubleFunction<? extends T> function, @NotNull Consumer<T> consumer) {
    myConsumer = value -> consumer.accept(function.apply(value));
  }

  public static <T> Animation withContext(@NotNull AnimationContext<T> context, @NotNull DoubleFunction<? extends T> function) {
    return new Animation(function, context);
  }

  /**
   * @return Delay in milliseconds.
   */
  public int getDelay() {
    return myDelay;
  }

  /**
   * @param delay in milliseconds.
   */
  @NotNull
  public Animation setDelay(int delay) {
    myDelay = Math.max(delay, 0);
    return this;
  }

  /**
   * @return Duration in milliseconds.
   */
  public int getDuration() {
    return myDuration;
  }

  /**
   * @param duration in milliseconds.
   */
  @NotNull
  public Animation setDuration(int duration) {
    myDuration = Math.max(duration, 0);
    return this;
  }

  /**
   * @return Finish time in milliseconds.
   */
  public int getFinish() {
    return myDelay + myDuration;
  }

  void update(double timeline) {
    myConsumer.accept(myEasing.calc(timeline));
  }

  @NotNull
  public Easing getEasing() {
    return myEasing;
  }

  @NotNull
  public Animation setEasing(@NotNull Easing easing) {
    myEasing = easing;
    return this;
  }

  @NotNull
  public Animation addListener(@NotNull Listener listener) {
    if (myListeners == null) {
      myListeners = new ArrayList<>();
    }
    myListeners.add(listener);
    return this;
  }

  /**
   * Runnable is called before first {@link Animation#update(double)} is called.
   * The time between animation is scheduled and updated can differ.
   */
  @NotNull
  public Animation runWhenScheduled(@NotNull Runnable runnable) {
    return addListener(Phase.SCHEDULED, runnable);
  }

  /**
   * Runnable is called right after {@link Animation#update(double)} is called.
   */
  @NotNull
  public Animation runWhenUpdated(@NotNull Runnable runnable) {
    return addListener(Phase.UPDATED, runnable);
  }

  /**
   * Runnable is called if animation is expired.
   */
  @NotNull
  public Animation runWhenExpired(@NotNull Runnable runnable) {
    return addListener(Phase.EXPIRED, runnable);
  }

  /**
   * Runnable is called if animation is cancelled but not expired.
   */
  @NotNull
  public Animation runWhenCancelled(@NotNull Runnable runnable) {
    return addListener(Phase.CANCELLED, runnable);
  }

  public Animation runWhenExpiredOrCancelled(@NotNull Runnable runnable) {
    return addListener(p -> {
      if (p == Phase.EXPIRED || p == Phase.CANCELLED) runnable.run();
    });
  }

  @NotNull
  private Animation addListener(@NotNull Phase phase, @NotNull Runnable runnable) {
    return addListener(p -> {
      if (p == phase) runnable.run();
    });
  }

  public void fireEvent(@NotNull Phase phase) {
    if (myListeners == null) {
      return;
    }
    Iterator<Listener> iterator = myListeners.iterator();
    while (iterator.hasNext()) {
      try {
        iterator.next().update(phase);
      } catch (Throwable t) {
        iterator.remove();
        Logger.getInstance(Animation.class).error("Listener caused an error and was removed from listeners", t);
      }
    }
  }

  @FunctionalInterface
  public interface Listener {
    void update(@NotNull Phase phase);
  }

  /**
   * <p>Any animation has 3 state:</p>
   *
   * <ol>
   *   <li>animation is scheduled for execution and goes to animation queue</li>
   *   <li>animation is updated on current animation cycle</li>
   *   <li>animation is expired and removed from the animation queue</li>
   *   <li>animation is cancelled</li>
   * </ol>
   *
   * For any animation it is always true that 'scheduled' is called before 'updated',
   * and updated is called before 'expired'. In some case they can be called
   * in one animation cycle, but usually 'scheduled' is called together with the first 'updated',
   * ant the last 'updated' called with 'expired'.
   */
  public enum Phase {
    SCHEDULED, UPDATED, EXPIRED, CANCELLED
  }
}
