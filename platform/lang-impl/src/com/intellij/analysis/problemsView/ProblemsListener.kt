// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.analysis.problemsView

import com.intellij.util.messages.Topic

interface ProblemsListener {
  companion object {
    @JvmField
    val TOPIC: Topic<ProblemsListener> = Topic(ProblemsListener::class.java)
  }

  fun problemAppeared(problem: Problem)
  fun problemDisappeared(problem: Problem)
  fun problemUpdated(problem: Problem)
}
