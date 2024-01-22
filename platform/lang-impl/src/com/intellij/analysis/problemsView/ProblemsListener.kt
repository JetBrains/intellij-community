// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView

import com.intellij.util.messages.Topic

/**
 * All methods are expected to be called in EDT.
 */
interface ProblemsListener {
  companion object {

    @JvmField
    @Topic.ProjectLevel
    val TOPIC: Topic<ProblemsListener> = Topic(ProblemsListener::class.java)
  }

  fun problemAppeared(problem: Problem)
  fun problemDisappeared(problem: Problem)
  fun problemUpdated(problem: Problem)
}
