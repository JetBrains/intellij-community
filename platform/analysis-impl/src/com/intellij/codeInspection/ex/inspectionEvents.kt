// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ex

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.util.TimeoutUtil
import java.util.concurrent.Callable

fun reportWhenInspectionFinished(inspectListener: InspectListener,
                                 toolWrapper: InspectionToolWrapper<*, *>,
                                 kind: InspectListener.InspectionKind,
                                 file: PsiFile?,
                                 project: Project,
                                 inspectAction: Callable<Int>) {
  val start = System.nanoTime()
  var problemsCount = -1
  try {
    problemsCount = inspectAction.call()
  }
  catch (e: Exception) {
    inspectListener.inspectionFailed(toolWrapper.tool.getShortName(), e, file, project)
    throw e
  }
  finally {
    inspectListener.inspectionFinished(TimeoutUtil.getDurationMillis(start), Thread.currentThread().id, problemsCount, toolWrapper,
                                       kind, file, project)
  }
}

fun reportWhenActivityFinished(inspectListener: InspectListener,
                               activityKind: InspectListener.ActivityKind,
                               project: Project,
                               activity: Runnable) {
  val start = System.currentTimeMillis()
  try {
    activity.run()
  }
  finally {
    inspectListener.activityFinished(System.currentTimeMillis() - start, Thread.currentThread().id, activityKind, project)
  }
}