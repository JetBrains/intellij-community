// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ex

fun reportWhenInspectionFinished(inspectListener: InspectListener,
                                 toolWrapper: InspectionToolWrapper<*, *>,
                                 kind: InspectListener.InspectionKind,
                                 inspectAction: Runnable) {
  val start = System.currentTimeMillis()
  try {
    inspectAction.run()
  }
  finally {
    inspectListener.inspectionFinished(System.currentTimeMillis() - start, Thread.currentThread().id, toolWrapper, kind)
  }
}

fun reportWhenActivityFinished(inspectListener: InspectListener, activityKind: InspectListener.ActivityKind, activity: Runnable) {
  val start = System.currentTimeMillis()
  try {
    activity.run()
  }
  finally {
    inspectListener.activityFinished(System.currentTimeMillis() - start, Thread.currentThread().id, activityKind)
  }
}