// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ex

import com.intellij.codeInsight.util.InspectionTracer
import com.intellij.openapi.project.Project
import com.intellij.platform.diagnostic.telemetry.TracerLevel
import com.intellij.platform.diagnostic.telemetry.TracerLevel.DETAILED
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.psi.PsiFile
import com.intellij.util.TimeoutUtil
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.Callable

@ApiStatus.Internal
fun reportToQodanaWhenInspectionFinished(inspectListener: InspectListener,
                                         toolWrapper: InspectionToolWrapper<*, *>,
                                         globalSimple : Boolean,
                                         psiFile: PsiFile?,
                                         project: Project,
                                         inspectAction: Callable<Int>) {
  val start = System.nanoTime()
  var problemsCount = -1
  try {
    InspectionTracer.spanBuilder(spanName = "inspectionRun", level = DETAILED).use { span ->
      if (span.isRecording) {
        span.setAttribute("inspectionId", toolWrapper.tool.shortName)
        val path = psiFile?.virtualFile?.path
        if (path != null) {
          span.setAttribute("file", path)
        }
      }
      problemsCount = inspectAction.call()
    }
  }
  catch (e: Exception) {
    inspectListener.inspectionFailed(toolWrapper.tool.getShortName(), e, psiFile, project)
    throw e
  }
  finally {
    inspectListener.inspectionFinished(TimeoutUtil.getDurationMillis(start), Thread.currentThread().id, problemsCount, toolWrapper,
                                       if (globalSimple) InspectListener.InspectionKind.GLOBAL_SIMPLE else InspectListener.InspectionKind.GLOBAL, psiFile, project)
  }
}

@ApiStatus.Internal
fun reportToQodanaWhenActivityFinished(inspectListener: InspectListener,
                                       activityKind: String,
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

@ApiStatus.Internal
suspend fun reportToQodanaWhenActivityFinished(
  inspectListener: InspectListener,
  activityKind: String,
  project: Project,
  activity: suspend () -> Unit
) {
  val start = System.currentTimeMillis()
  try {
    activity()
  } finally {
    inspectListener.activityFinished(
      System.currentTimeMillis() - start,
      Thread.currentThread().id,
      activityKind,
      project
    )
  }
}