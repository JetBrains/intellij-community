// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.codereview.issues

import com.intellij.collaboration.ui.codereview.issues.processIssueIdsMarkdown
import com.intellij.openapi.vcs.IssueNavigationConfiguration
import com.intellij.openapi.vcs.IssueNavigationLink
import com.intellij.testFramework.HeavyPlatformTestCase

internal class CodeReviewIssueIdsProcessorTest : HeavyPlatformTestCase() {

  override fun setUp() {
    super.setUp()
    // setup youtrack issues handling like IDEA-1234
    IssueNavigationConfiguration.getInstance(project).links = listOf(
      IssueNavigationLink("[A-Z]+\\-\\d+", "https://youtrack.jetbrains.com/issue/\$0")
    )
  }

  // IDEA-261255
  fun `test youtrack issue substitute`() {
    val issueId = "IDEA-1234"
    val markdown = "Check youtrack issue: $issueId. Should be link"

    markdown shouldBeConvertedTo "Check youtrack issue: [$issueId](https://youtrack.jetbrains.com/issue/$issueId). Should be link"
  }

  // IDEA-278800
  fun `test markdown with links should stay same`() {
    val markdown = "Link [hello](https://google.com) should be the same"

    markdown shouldBeConvertedTo markdown
  }

  fun `test markdown with links and youtrack issue`() {
    val issueId = "IDEA-1234"
    val markdown = "Link [hello](https://google.com). Youtrack issue: $issueId"

    markdown shouldBeConvertedTo "Link [hello](https://google.com). Youtrack issue: [$issueId](https://youtrack.jetbrains.com/issue/$issueId)"
  }

  fun `test link with issue inside should stay same`() {
    val issueId = "IDEA-1234"
    val markdown = "Link with issue inside: [hello](https://google.com/$issueId)"

    markdown shouldBeConvertedTo markdown
  }

  // TODO: fix IDEA-280335
  fun `markdown link name with issue inside should stay same`() {
    val issueId = "IDEA-1234"
    val markdown = "Link with issue inside: [Issue: $issueId](https://google.com)"


    markdown shouldBeConvertedTo markdown
  }

  private infix fun String.shouldBeConvertedTo(expected: String) {
    val actual = processIssueIdsMarkdown(project, this)
    assertEquals(expected, actual)
  }
}