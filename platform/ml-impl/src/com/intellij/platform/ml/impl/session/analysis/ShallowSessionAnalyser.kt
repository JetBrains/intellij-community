// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.session.analysis

import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.ObjectDescription
import com.intellij.platform.ml.Environment
import org.jetbrains.annotations.ApiStatus

/**
 * Analyzes not the ML session's tree, but some generic information.
 * Could be used to analyze started, as well as failed to start sessions.
 */
@ApiStatus.Internal
interface ShallowSessionAnalyser<D> {
  /**
   * Name of the analyzer.
   */
  val name: String

  /**
   * A complete static declaration of the fields, that will be written during analysis.
   */
  val declaration: List<EventField<*>>

  /**
   * Analyze some generic information about an ML session.
   *
   * @param permanentSessionEnvironment The environment that is available during the whole ML session
   * @param data Some additional data, an insight about the place where the analyzer was called.
   * Could be a [Throwable], or a reason why it was not possible to start the session.
   */
  fun analyse(permanentSessionEnvironment: Environment, data: D): List<EventPair<*>>

  companion object {
    val <F> ShallowSessionAnalyser<F>.declarationObjectDescription: ObjectDescription
      get() = object : ObjectDescription() {
        init {
          declaration.forEach { field(it) }
        }
      }
  }
}
