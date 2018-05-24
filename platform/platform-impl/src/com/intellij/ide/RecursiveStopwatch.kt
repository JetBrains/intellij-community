/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:JvmName("RecursiveStopwatchUtil")
package com.intellij.ide

/**
 * A stopwatch that can be used to efficiently measure recursive operations. Although it works
 * fine as a standalone stopwatch, it also allows its state to be saved and restored. This can
 * be used to build a stack of stopwatches where time is accumulated only on the topmost stopwatch
 * on the stack.
 *
 * The [start] method pauses any ongoing stopwatch and returns its state as a long. Then it starts a new
 * timer. The [end] method pops a stopwatch off the stack, returning its final value. It optionally
 * accepts a state returned from [start], in which case it will resume that timer.
 *
 * Saving and restoring the stopwatch state is useful for timing recursive operations, where time
 * for an inner operation should not be counted towards the time for its parent.
 *
 * This class is only visible for testing. Clients should not use it.
 *
 * @param clock function that returns the current time as a long
 */
class RecursiveStopwatch(val clock: () -> Long) {
  /**
   * The start time, or -1 if the stopwatch is stopped or paused.
   */
  var myStartTime = -1L

  /**
   * The amount of time accumulated on the stopwatch, prior to it being paused. -1 if the stopwatch
   * is currently stopped.
   */
  var myAccumulated = -1L

  constructor() : this({ System.currentTimeMillis() })

  /**
   * Restarts the stopwatch from 0ms. Returns the existing time that had been on the stopwatch
   * or -1 if it had not been started.
   */
  fun start(): Long {
    val currentTime = clock()
    val result = if (myStartTime == -1L) myAccumulated else myAccumulated + currentTime - myStartTime
    myStartTime = currentTime
    myAccumulated = 0L
    return result
  }

  /**
   * Stops the current timer and optionally resumes a previously-running timer.
   * Returns the elapsed time (ms) on the stopwatch or -1 if it hadn't been started.
   *
   * @param stateToResume If this is -1, the stopwatch will be stopped when the method
   * returns. If it is non-negative, the stopwatch will be running starting from that
   * number of milliseconds.
   */
  fun end(stateToResume: Long = -1): Long {
    if (stateToResume == -1L && myAccumulated == -1L) {
      return -1L
    }
    var result = -1L
    val currentTime = clock()
    if (myAccumulated != -1L) {
      if (myStartTime == -1L) {
        result = myAccumulated
      }
      else {
        result = myAccumulated + currentTime - myStartTime
      }
    }
    if (stateToResume != -1L) {
      myStartTime = currentTime
    }
    else {
      myStartTime = -1L
    }
    myAccumulated = stateToResume

    return result
  }

  /**
   * Pauses the watch. Returns the elapsed time (ms) on the stopwatch or -1 if
   * the watch hadn't been started. If the watch is already paused, this will
   * have no effect and will return the same result as the call that paused
   * the watch.
   */
  fun pause(): Long {
    if (myStartTime != -1L) {
      myAccumulated += clock() - myStartTime;
      myStartTime = -1L
    }
    return myAccumulated
  }

  /**
   * Resumes the stopwatch after a call to [pause].
   */
  fun resume() {
    if (myAccumulated == -1L) {
      return;
    }
    if (myStartTime == -1L) {
      myStartTime = clock()
    }
  }
}

