// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex

import com.intellij.codeInsight.util.InspectionTracer
import com.intellij.codeInspection.AggregateResultsInspection
import com.intellij.codeInspection.CommonProblemDescriptor
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.platform.diagnostic.telemetry.TracerLevel.DETAILED
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.psi.PsiFile
import com.intellij.util.TimeoutUtil
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.Callable

@ApiStatus.Internal
fun reportToQodanaWhenInspectionFinished(context: GlobalInspectionContextEx,
                                         toolWrapper: InspectionToolWrapper<*, *>,
                                         kind: InspectListener.InspectionKind,
                                         psiFile: PsiFile?,
                                         inspectAction: Callable<out Collection<CommonProblemDescriptor>>) {
  val start = System.nanoTime()
  var descriptors: Collection<CommonProblemDescriptor>? = null
  val project = context.project
  val publisher = project.messageBus.syncPublisher(GlobalInspectionContextEx.INSPECT_TOPIC)
  try {
    InspectionTracer.spanBuilder(spanName = "inspectionRun", level = DETAILED).use { span ->
      if (span.isRecording) {
        span.setAttribute("inspectionId", toolWrapper.tool.shortName)
        val path = psiFile?.virtualFile?.path
        if (path != null) {
          span.setAttribute("file", path)
        }
      }
      descriptors = inspectAction.call()
    }
  }
  catch (e: Exception) {
    publisher.inspectionFailed(toolWrapper.tool.shortName, e, psiFile, project)
    throw e
  }
  finally {
    val duration = TimeoutUtil.getDurationMillis(start)
    val threadId = Thread.currentThread().threadId()
    val problemsCount = descriptors?.size ?: -1
    publisher.inspectionFinished(duration, threadId, problemsCount, toolWrapper, kind, psiFile, project)

    if (descriptors != null && toolWrapper.tool is AggregateResultsInspection) {
      val problemsByRule = descriptors.groupBy { (it as? ProblemDescriptor)?.problemGroup?.problemName }
      for ((ruleId, ruleDescriptors) in problemsByRule) {
        if (ruleId != null && ruleId != toolWrapper.shortName) {
          val childWrapper = context.tools[ruleId]?.tool
          if (childWrapper != null) {
            publisher.inspectionFinished(duration, threadId, ruleDescriptors.size, childWrapper, kind, psiFile, project)
          }
        }
      }
    }
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
    inspectListener.activityFinished(System.currentTimeMillis() - start, Thread.currentThread().threadId(), activityKind, project)
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
      Thread.currentThread().threadId(),
      activityKind,
      project
    )
  }
}