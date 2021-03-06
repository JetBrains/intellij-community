// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jsonpath.ui

import com.intellij.json.JsonLanguage
import com.intellij.json.json5.Json5Language
import com.intellij.json.psi.JsonFile
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.PsiAnchor
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update

internal class JsonPathEvaluateFileView(project: Project, jsonFile: JsonFile) : JsonPathEvaluateView(project) {
  private val expressionHighlightingQueue: MergingUpdateQueue = MergingUpdateQueue("JSONPATH_EVALUATE", 1000, true, null, this)
  private val fileAnchor: PsiAnchor = PsiAnchor.create(jsonFile)
  private val jsonChangeTrackers: List<ModificationTracker> = listOf(JsonLanguage.INSTANCE, Json5Language.INSTANCE).map {
    PsiModificationTracker.SERVICE.getInstance(project).forLanguage(it)
  }
  @Volatile
  private var previousModificationCount: Long = 0

  init {
    val content = BorderLayoutPanel()

    content.addToTop(searchWrapper)
    content.addToCenter(resultWrapper)

    setContent(content)

    initToolbar()

    val messageBusConnection = this.project.messageBus.connect(this)
    messageBusConnection.subscribe(PsiModificationTracker.TOPIC, PsiModificationTracker.Listener {
      expressionHighlightingQueue.queue(Update.create(this@JsonPathEvaluateFileView) {
        detectChangesInJson()
      })
    })
  }

  private fun detectChangesInJson() {
    val count = previousModificationCount
    val newCount = jsonChangeTrackers.sumOf { it.modificationCount }
    if (newCount != count) {
      previousModificationCount = newCount
      // some JSON documents have been changed
      resetExpressionHighlighting()
    }
  }

  public override fun getJsonFile(): JsonFile? {
    return fileAnchor.retrieve() as? JsonFile
  }
}