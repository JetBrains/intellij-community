// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.json.JsonBundle
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.jsonSchema.remote.JsonFileResolver
import com.jetbrains.jsonSchema.remote.http.JsonSchemaRemoteContentService
import com.jetbrains.jsonSchema.remote.http.RemoteDownloadFailure

class JsonSchemaRemoteDownloadAnnotator : Annotator, DumbAware {
  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    val rangeAndUrl = when {
      element is JsonStringLiteral && (element.parent as? JsonProperty)?.name == "\$schema" ->
        element.textRange to JsonFileResolver.replaceUnsafeSchemaStoreUrls(element.value)
      element is PsiComment -> {
        val r = JsonSchemaByCommentProvider.detectInComment(element.text) ?: return
        element.textRange.cutOut(r) to r.substring(element.text)
      }
      else -> return
    }
    val url = rangeAndUrl.second ?: return
    if (!JsonFileResolver.isHttpPath(url)) return
    val remote = JsonSchemaRemoteContentService.getInstance(element.project)
    if (!remote.isAllowed()) return
    if (remote.hasInFlight(url)) return
    if (remote.peekCachedFile(url) != null) return
    val range = rangeAndUrl.first
    if (remote.isUnavailable(url)) {
      holder.newAnnotation(HighlightSeverity.WARNING, JsonBundle.message("json.schema.remote.download.unavailable", url))
        .range(range)
        .withFix(RetrySchemaDownloadFix(url))
        .create()
      return
    }
    if (remote.lastFailure(url) == RemoteDownloadFailure.Rejected) {
      holder.newAnnotation(HighlightSeverity.WARNING, JsonBundle.message("json.schema.remote.download.unavailable", url))
        .range(range)
        .create()
      return
    }
    if (remote.lastFailure(url) == RemoteDownloadFailure.Failed) {
      holder.newAnnotation(HighlightSeverity.WEAK_WARNING, JsonBundle.message("json.schema.remote.download.failed"))
        .range(range)
        .withFix(RetrySchemaDownloadFix(url))
        .create()
    }
  }
}

private class RetrySchemaDownloadFix(private val url: String) : IntentionAction {
  override fun getText(): String = JsonBundle.message("json.schema.remote.download.retry")

  override fun getFamilyName(): String = text

  override fun isAvailable(project: Project, editor: Editor?, psiFile: PsiFile?): Boolean {
    val remote = JsonSchemaRemoteContentService.getInstance(project)
    return remote.lastFailure(url) == RemoteDownloadFailure.Failed || remote.isUnavailable(url)
  }

  override fun invoke(project: Project, editor: Editor?, psiFile: PsiFile?) {
    JsonSchemaRemoteContentService.getInstance(project).retryDownload(url)
  }

  override fun startInWriteAction(): Boolean = false

  override fun generatePreview(project: Project, editor: Editor, psiFile: PsiFile): IntentionPreviewInfo = IntentionPreviewInfo.EMPTY
}
