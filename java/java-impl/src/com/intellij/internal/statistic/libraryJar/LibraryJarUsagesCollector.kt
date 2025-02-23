// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.libraryJar

import com.intellij.internal.statistic.LibraryNameValidationRule
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventFields.StringValidatedByCustomRule
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.project.DumbService.Companion.getInstance
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.ProjectScope

internal class LibraryJarUsagesCollector : ProjectUsagesCollector() {
  private val GROUP = EventLogGroup("javaLibraryJars", 4)
  private val USED_LIBRARY = GROUP.registerEvent("used.library",
                                                 EventFields.Version,
                                                 StringValidatedByCustomRule("library", LibraryNameValidationRule::class.java))

  override fun getGroup(): EventLogGroup = GROUP

  override suspend fun collect(project: Project): Set<MetricEvent> {
    val descriptors = LibraryJarStatisticsService.getInstance().getTechnologyDescriptors()
    val result: MutableSet<MetricEvent> = HashSet(descriptors.size)

    for (descriptor in descriptors) {
      val className = descriptor.myClass
      if (className == null) continue

      val jarVirtualFiles = smartReadAction(project) {
        val psiClasses = getInstance(project).computeWithAlternativeResolveEnabled<Array<PsiClass>, RuntimeException> {
          JavaPsiFacade.getInstance(project).findClasses(className, ProjectScope.getLibrariesScope(project))
        }
        psiClasses.mapNotNull {
          it.findCorrespondingVirtualFile()
        }
      }

      for (jarVirtualFile in jarVirtualFiles) {
        val version = findJarVersion(jarVirtualFile)
        if (StringUtil.isNotEmpty(version)) {
          result.add(USED_LIBRARY.metric(version, descriptor.myName))
        }
      }
    }

    return result
  }
}