// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.ide.impl

import com.intellij.codeInsight.documentation.actions.DocumentationDownloader
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId2
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.lang.documentation.psi.PsiElementDocumentationTarget
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.documentation.DocumentationTarget

object DocumentationUsageCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("documentation", 5)

  private val DOWNLOAD_FINISHED_EVENT = GROUP.registerEvent("quick.doc.download.finished",
                                                            EventFields.Class("handler"),
                                                            EventFields.Boolean("success")
  )

  val QUICK_DOC_SHOWN = GROUP.registerEvent("quick.doc.shown", EventFields.FileType)
  val QUICK_DOC_CLOSED = GROUP.registerEvent("quick.doc.closed", EventFields.FileType, EventFields.Boolean("joint"), EventFields.DurationMs)
  val DOC_COMPUTED = GROUP.registerEvent("computed", EventFields.Class("refClass"), EventFields.RoundedInt("size"))

  val EXPANDABLE_DEFINITION_SHOWN = GROUP.registerEvent("expandable.definition.shown")
  val EXPANDABLE_DEFINITION_EXPANDED = GROUP.registerEvent("expandable.definition.expanded", EventFields.Boolean("expand"))

  val DOCUMENTATION_LINK_CLICKED: EventId2<DocumentationLinkProtocol, Boolean> = GROUP.registerEvent(
    "quick.doc.link.clicked",
    EventFields.Enum("protocol", DocumentationLinkProtocol::class.java),
    EventFields.Boolean("lookup_active"),
  )

  fun logDownloadFinished(project: Project, handlerClass: Class<out DocumentationDownloader>, success: Boolean) {
    DOWNLOAD_FINISHED_EVENT.log(project, handlerClass, success)
  }
}

internal fun getClassRefForStatistics(dereference: DocumentationTarget?): Class<out Any>? {
  @Suppress("TestOnlyProblems")
  return if (dereference is PsiElementDocumentationTarget)
    dereference.targetElement.javaClass
  else
    dereference?.javaClass
}

enum class DocumentationLinkProtocol {
  HTTP,
  HTTPS,
  PSI_ELEMENT,
  FILE,
  OTHER;

  companion object {
    fun of(url: String): DocumentationLinkProtocol {
      val prefix = url.takeWhile { it != ':' }
      return entries.firstOrNull { it.name.equals(prefix, true) }
             ?: OTHER
    }
  }
}