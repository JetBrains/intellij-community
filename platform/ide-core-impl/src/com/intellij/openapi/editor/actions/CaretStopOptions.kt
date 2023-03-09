// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions

import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.OptionTag


data class CaretStop
@JvmOverloads constructor(@Attribute("start") val isAtStart: Boolean = false,
                          @Attribute("end") val isAtEnd: Boolean = false) {
  companion object { // @formatter:off
    @JvmField val NONE  = CaretStop(isAtStart = false, isAtEnd = false)
    @JvmField val START = CaretStop(isAtStart = true,  isAtEnd = false)
    @JvmField val END   = CaretStop(isAtStart = false, isAtEnd = true)
    @JvmField val BOTH  = CaretStop(isAtStart = true,  isAtEnd = true)
  } // @formatter:on
}

data class CaretStopPolicy(@OptionTag("WORD") val wordStop: CaretStop = CaretStop.NONE,
                           @OptionTag("LINE") val lineStop: CaretStop = CaretStop.NONE) {
  companion object { // @formatter:off
    @JvmField val NONE       = CaretStopPolicy(wordStop = CaretStop.NONE,  lineStop = CaretStop.NONE)
    @JvmField val WORD_START = CaretStopPolicy(wordStop = CaretStop.START, lineStop = CaretStop.BOTH)
    @JvmField val WORD_END   = CaretStopPolicy(wordStop = CaretStop.END,   lineStop = CaretStop.BOTH)
    @JvmField val BOTH       = CaretStopPolicy(wordStop = CaretStop.BOTH,  lineStop = CaretStop.BOTH)
  } // @formatter:on
}

data class CaretStopOptions(@OptionTag("BACKWARD") val backwardPolicy: CaretStopPolicy,
                            @OptionTag("FORWARD") val forwardPolicy: CaretStopPolicy) {
  private constructor(other: CaretStopOptions) : this(backwardPolicy = other.backwardPolicy,
                                                      forwardPolicy = other.forwardPolicy)

  // for deserializing
  constructor() : this(CaretStopOptionsTransposed.DEFAULT.toCaretStopOptions())
}

// transposed human-friendly options view and constants

data class CaretStopBoundary(val backward: CaretStop = CaretStop.NONE,
                             val forward: CaretStop = CaretStop.NONE) {
  companion object { // @formatter:off
    @JvmField val NONE = CaretStopBoundary(backward = CaretStop.NONE, forward = CaretStop.NONE)
    @JvmField val CURRENT = CaretStopBoundary(backward = CaretStop.START, forward = CaretStop.END)
    @JvmField val NEIGHBOR = CaretStopBoundary(backward = CaretStop.END, forward = CaretStop.START)
    @JvmField val START = CaretStopBoundary(backward = CaretStop.START, forward = CaretStop.START)
    @JvmField val END = CaretStopBoundary(backward = CaretStop.END, forward = CaretStop.END)
    @JvmField val BOTH = CaretStopBoundary(backward = CaretStop.BOTH, forward = CaretStop.BOTH)
  } // @formatter:on
}

/**
 * While using CaretStopOptions is more convenient from the code point of view (chain: backward/forward -> word/line -> start/end),
 * it is more easy to reason about when the direction and boundary axes are switched (word/line -> backward/forward -> start/end).
 */
data class CaretStopOptionsTransposed(val wordBoundary: CaretStopBoundary,
                                      val lineBoundary: CaretStopBoundary) {
  fun toCaretStopOptions() =
    CaretStopOptions(backwardPolicy = CaretStopPolicy(wordStop = wordBoundary.backward,
                                                      lineStop = lineBoundary.backward),
                     forwardPolicy = CaretStopPolicy(wordStop = wordBoundary.forward,
                                                     lineStop = lineBoundary.forward))

  companion object {
    @JvmField
    val DEFAULT_WINDOWS = CaretStopOptionsTransposed(wordBoundary = CaretStopBoundary.START,
                                                     lineBoundary = CaretStopBoundary.BOTH)
    @JvmField
    val DEFAULT_UNIX = CaretStopOptionsTransposed(wordBoundary = CaretStopBoundary.CURRENT,
                                                  lineBoundary = CaretStopBoundary.NONE)
    @JvmField
    val DEFAULT = CaretStopOptionsTransposed(wordBoundary = CaretStopBoundary.CURRENT,
                                             lineBoundary = CaretStopBoundary.NEIGHBOR)

    @JvmStatic
    fun fromCaretStopOptions(caretStopOptions: CaretStopOptions) = with(caretStopOptions) {
      CaretStopOptionsTransposed(wordBoundary = CaretStopBoundary(backward = backwardPolicy.wordStop,
                                                                  forward = forwardPolicy.wordStop),
                                 lineBoundary = CaretStopBoundary(backward = backwardPolicy.lineStop,
                                                                  forward = forwardPolicy.lineStop))
    }
  }
}
