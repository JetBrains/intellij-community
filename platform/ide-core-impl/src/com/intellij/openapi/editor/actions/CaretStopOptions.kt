// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions

import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.OptionTag
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class CaretStop
@JvmOverloads constructor(@Attribute("start") val isAtStart: Boolean = false,
                          @Attribute("end") val isAtEnd: Boolean = false) {
  companion object { // @formatter:off
    @JvmField val NONE:CaretStop  = CaretStop(isAtStart = false, isAtEnd = false)
    @JvmField val START:CaretStop = CaretStop(isAtStart = true,  isAtEnd = false)
    @JvmField val END:CaretStop   = CaretStop(isAtStart = false, isAtEnd = true)
    @JvmField val BOTH:CaretStop  = CaretStop(isAtStart = true,  isAtEnd = true)
  } // @formatter:on
}

@ApiStatus.Internal
data class CaretStopPolicy(@OptionTag("WORD") val wordStop: CaretStop = CaretStop.NONE,
                           @OptionTag("LINE") val lineStop: CaretStop = CaretStop.NONE) {
  companion object { // @formatter:off
    @JvmField val NONE:CaretStopPolicy       = CaretStopPolicy(wordStop = CaretStop.NONE,  lineStop = CaretStop.NONE)
    @JvmField val WORD_START:CaretStopPolicy = CaretStopPolicy(wordStop = CaretStop.START, lineStop = CaretStop.BOTH)
    @JvmField val WORD_END:CaretStopPolicy   = CaretStopPolicy(wordStop = CaretStop.END,   lineStop = CaretStop.BOTH)
    @JvmField val BOTH:CaretStopPolicy       = CaretStopPolicy(wordStop = CaretStop.BOTH,  lineStop = CaretStop.BOTH)
  } // @formatter:on
}

@ApiStatus.Internal
data class CaretStopOptions(@OptionTag("BACKWARD") val backwardPolicy: CaretStopPolicy,
                            @OptionTag("FORWARD") val forwardPolicy: CaretStopPolicy) {
  private constructor(other: CaretStopOptions) : this(backwardPolicy = other.backwardPolicy,
                                                      forwardPolicy = other.forwardPolicy)

  // for deserializing
  constructor() : this(CaretStopOptionsTransposed.DEFAULT.toCaretStopOptions())
}

// transposed human-friendly options view and constants

@ApiStatus.Internal
data class CaretStopBoundary(val backward: CaretStop = CaretStop.NONE,
                             val forward: CaretStop = CaretStop.NONE) {
  companion object { // @formatter:off
    @JvmField val NONE:CaretStopBoundary = CaretStopBoundary(backward = CaretStop.NONE, forward = CaretStop.NONE)
    @JvmField val CURRENT:CaretStopBoundary = CaretStopBoundary(backward = CaretStop.START, forward = CaretStop.END)
    @JvmField val NEIGHBOR:CaretStopBoundary = CaretStopBoundary(backward = CaretStop.END, forward = CaretStop.START)
    @JvmField val START:CaretStopBoundary = CaretStopBoundary(backward = CaretStop.START, forward = CaretStop.START)
    @JvmField val END:CaretStopBoundary = CaretStopBoundary(backward = CaretStop.END, forward = CaretStop.END)
    @JvmField val BOTH:CaretStopBoundary = CaretStopBoundary(backward = CaretStop.BOTH, forward = CaretStop.BOTH)
  } // @formatter:on
}

/**
 * While using CaretStopOptions is more convenient from the code point of view (chain: backward/forward -> word/line -> start/end),
 * it is more easy to reason about when the direction and boundary axes are switched (word/line -> backward/forward -> start/end).
 */
@ApiStatus.Internal
data class CaretStopOptionsTransposed(val wordBoundary: CaretStopBoundary,
                                      val lineBoundary: CaretStopBoundary) {
  fun toCaretStopOptions(): CaretStopOptions =
    CaretStopOptions(backwardPolicy = CaretStopPolicy(wordStop = wordBoundary.backward,
                                                      lineStop = lineBoundary.backward),
                     forwardPolicy = CaretStopPolicy(wordStop = wordBoundary.forward,
                                                     lineStop = lineBoundary.forward))

  companion object {
    @JvmField
    val DEFAULT_WINDOWS: CaretStopOptionsTransposed = CaretStopOptionsTransposed(wordBoundary = CaretStopBoundary.START,
                                                                                 lineBoundary = CaretStopBoundary.BOTH)
    @JvmField
    val DEFAULT_UNIX: CaretStopOptionsTransposed = CaretStopOptionsTransposed(wordBoundary = CaretStopBoundary.CURRENT,
                                                                              lineBoundary = CaretStopBoundary.NONE)
    @JvmField
    val DEFAULT: CaretStopOptionsTransposed = CaretStopOptionsTransposed(wordBoundary = CaretStopBoundary.CURRENT,
                                                                         lineBoundary = CaretStopBoundary.NEIGHBOR)

    @JvmStatic
    fun fromCaretStopOptions(caretStopOptions: CaretStopOptions): CaretStopOptionsTransposed = with(caretStopOptions) {
      CaretStopOptionsTransposed(wordBoundary = CaretStopBoundary(backward = backwardPolicy.wordStop,
                                                                  forward = forwardPolicy.wordStop),
                                 lineBoundary = CaretStopBoundary(backward = backwardPolicy.lineStop,
                                                                  forward = forwardPolicy.lineStop))
    }
  }
}
