// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.issue

import com.intellij.build.events.BuildEventsNls
import com.intellij.lang.LangBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.pom.Navigatable
import org.jetbrains.annotations.CheckReturnValue

abstract class ConfigurableBuildIssue : BuildIssue {

  private val configurator = BuildIssueConfigurator()

  final override val title: @BuildEventsNls.Title String
    get() = configurator.title

  final override val quickFixes: List<BuildIssueQuickFix>
    get() = configurator.quickFixes

  final override val description: @BuildEventsNls.Description String
    get() = configurator.createDescription()

  override fun getNavigatable(project: Project): Navigatable? = null

  fun setTitle(title: @BuildEventsNls.Title String) {
    configurator.title = title
  }

  fun addDescription(description: @BuildEventsNls.Description String) {
    configurator.description.add(description)
  }

  fun addQuickFixPrompt(quickFixPrompt: @BuildEventsNls.Description String) {
    configurator.quickFixPrompts.add(quickFixPrompt)
  }

  /**
   * Defines quick fix implementation that will be called on hyperlink activation.
   * The hyperlink in the quick fix prompt should use href attribute which returns from this function.
   * @return hyperlink reference (href) for the quickfix prompt
   */
  @CheckReturnValue
  fun addQuickFix(quickFix: BuildIssueQuickFix): String {
    val ordinal = configurator.quickFixes.size
    val hyperlinkReference = "${quickFix.id}($ordinal)"
    val orderedQuickFix = QuickFix(hyperlinkReference, quickFix)
    configurator.quickFixes.add(orderedQuickFix)
    return hyperlinkReference
  }

  private class QuickFix(
    hyperlinkReference: String,
    private val delegate: BuildIssueQuickFix
  ) : BuildIssueQuickFix by delegate {

    override val id: String = hyperlinkReference
  }

  private class BuildIssueConfigurator {

    lateinit var title: @BuildEventsNls.Title String
    val description: MutableList<@BuildEventsNls.Description String> = ArrayList()
    val quickFixPrompts: MutableList<@BuildEventsNls.Description String> = ArrayList()
    val quickFixes: MutableList<BuildIssueQuickFix> = ArrayList()

    fun createDescription(): @NlsSafe String {
      return buildString {
        append(description.joinToString("\n\n"))
          .append("\n")
        if (quickFixPrompts.isNotEmpty()) {
          append("\n")
          append(LangBundle.message("build.issue.quick.fix.title", quickFixPrompts.size))
          append("\n")
          append(quickFixPrompts.joinToString("\n") { " - $it" })
          append("\n")
        }
      }
    }
  }
}