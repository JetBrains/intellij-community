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
  private @NotNull Easing myEasing = Easing.LINEAR;
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

  public int getDelay() {
    return myDelay;
  }

  @NotNull
  public Animation setDelay(int delay) {
    myDelay = Math.max(delay, 0);
    return this;
  }

  public int getDuration() {
    return myDuration;
  }

  @NotNull
  public Animation setDuration(int duration) {
    myDuration = Math.max(duration, 0);
    return this;
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

  @NotNull
  public Animation runWhenScheduled(@NotNull Runnable runnable) {
    return addListener(Phase.SCHEDULED, runnable);
  }

  @NotNull
  public Animation runWhenUpdated(@NotNull Runnable runnable) {
    return addListener(Phase.UPDATED, runnable);
  }

  @NotNull
  public Animation runWhenExpired(@NotNull Runnable runnable) {
    return addListener(Phase.EXPIRED, runnable);
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
        Logger.getInstance(JBAnimator.class).error("Listener caused an error and was removed from listeners", t);
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
   * </ol>
   *
   * For any animation it is always true that 'scheduled' is called before 'updated',
   * and updated is called before 'expired'. In some case they can be called
   * in one animation cycle, but usually 'scheduled' is called together with the first 'updated',
   * ant the last 'updated' called with 'expired'.
   */
  public enum Phase {
    SCHEDULED, UPDATED, EXPIRED
  }
}
