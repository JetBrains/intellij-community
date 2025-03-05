// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs.statistics

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * @author Vitaliy.Bibaev
 */
interface UserFactorStorage {
  companion object {
    fun getInstance(): UserFactorStorage = service<ApplicationInlineFactorStorage>()
    fun getInstance(project: Project): UserFactorStorage = project.service<ProjectUserFactorStorage>()

    fun <U : FactorUpdater> applyOnBoth(project: Project, description: UserFactorDescription<U, *>, updater: (U) -> Unit) {
      updater(getInstance().getFactorUpdater(description))
      updater(getInstance(project).getFactorUpdater(description))
    }

    fun <U : FactorUpdater> apply(description: UserFactorDescription<U, *>, updater: (U) -> Unit) {
      updater(getInstance().getFactorUpdater(description))
    }
  }

  fun <U : FactorUpdater> getFactorUpdater(description: UserFactorDescription<U, *>): U
  fun <R : FactorReader> getFactorReader(description: UserFactorDescription<*, R>): R
}