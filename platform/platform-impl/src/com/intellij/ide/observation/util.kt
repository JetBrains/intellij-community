// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ObservationUtil")
package com.intellij.ide.observation

import com.intellij.configurationStore.saveProjectsAndApp
import com.intellij.openapi.progress.indeterminateStep
import com.intellij.openapi.progress.rawProgressReporter
import com.intellij.openapi.progress.withRawProgressReporter
import com.intellij.openapi.project.Project
import kotlinx.coroutines.delay
import kotlin.coroutines.coroutineContext

object Observation {
  
  /**
   * Suspends until configuration processes in the IDE are completed.
   */
  @JvmStatic
  suspend fun awaitConfigurationPredicates(project: Project) {
    indeterminateStep {
      withRawProgressReporter {
        while (true) {
          val wasModified = doAwaitConfigurationPredicates(project)
          if (!wasModified) {
            rawProgressReporter?.text("The project is configured completely.") // NON-NLS
            break
          } else {
            rawProgressReporter?.text("Modified files are saved. Checking if it triggered configuration process...") // NON-NLS
          }
        }
      }
    }
  }

  private suspend fun doAwaitConfigurationPredicates(project: Project) : Boolean {
    var isModificationOccurred = false
    predicateLoop@ while (true) {
      for (processBusyPredicate in ActivityInProgressPredicate.EP_NAME.extensionList) {
        if (processBusyPredicate.isInProgress(project)) {
          isModificationOccurred = true
          coroutineContext.rawProgressReporter?.text("'${processBusyPredicate.presentableName}' is in running...") // NON-NLS
          delay(1000)
          continue@predicateLoop
        }
      }
      break
    }

    coroutineContext.rawProgressReporter?.text("All predicates are completed. Saving files...") // NON-NLS

    saveProjectsAndApp(true)

    return isModificationOccurred
  }
}