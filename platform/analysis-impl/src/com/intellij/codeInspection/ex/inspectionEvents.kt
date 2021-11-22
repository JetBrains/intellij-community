// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ex

import java.util.concurrent.Callable
import com.intellij.openapi.project.Project

fun reportWhenInspectionFinished(inspectListener: InspectListener,
                                 toolWrapper: InspectionToolWrapper<*, *>,
                                 kind: InspectListener.InspectionKind,
                                 project: Project,
                                 inspectAction: Callable<Int>) {
  val start = System.currentTimeMillis()
  var problemsCount = -1
  try {
    problemsCount = inspectAction.call()
  }
  finally {
    inspectListener.inspectionFinished(System.currentTimeMillis() - start, Thread.currentThread().id, problemsCount, toolWrapper,
                                       kind, project)
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