// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.issues

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk.link
import com.intellij.openapi.vcs.IssueNavigationConfiguration
import com.intellij.util.io.URLUtil

fun markdownLink(text: String, target: String): @NlsSafe String = "[$text]($target)"

fun processIssueIdsMarkdown(project: Project, markdown: @NlsSafe String): @NlsSafe String {
  return processIssueIds(project, markdown) { text, target ->
    markdownLink(text, target)
  }
}

fun processIssueIdsHtml(project: Project, htmlText: @NlsSafe String): @NlsSafe String {
  return processIssueIds(project, htmlText) { text, target ->
    link(target, text).toString()
  }
}

private fun processIssueIds(
  project: Project,
  textToProcess: @NlsSafe String,
  linkConverter: (text: @NlsSafe String, target: @NlsSafe String) -> String
): @NlsSafe String {
  val markdownWithIssueLinksBuilder = StringBuilder()
  IssueNavigationConfiguration.processTextWithLinks(
    textToProcess,
    textToProcess.parseIssuesAndLinks(project).filter { !it.isPossiblyLink(textToProcess) },
    { text ->
      markdownWithIssueLinksBuilder.append(text)
    },
    { text, target ->
      markdownWithIssueLinksBuilder.append(linkConverter(text, target))
    }
  )
  return markdownWithIssueLinksBuilder.toString()
}

// We need to parse issues AND links since issue id can be located inside link and we shouldn't change the link
private fun String.parseIssuesAndLinks(project: Project) = IssueNavigationConfiguration.getInstance(project).findIssueLinks(this)

private fun IssueNavigationConfiguration.LinkMatch.isPossiblyLink(textToProcess: String): Boolean {
  return URLUtil.canContainUrl(this.range.subSequence(textToProcess).toString())
}