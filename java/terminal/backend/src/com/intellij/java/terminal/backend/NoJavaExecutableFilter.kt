// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.terminal.backend

import com.intellij.codeInsight.hints.presentation.InlayButtonPresentationFactory
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.codeInsight.hints.presentation.PresentationRenderer
import com.intellij.execution.filters.ConsoleFilterProviderEx
import com.intellij.execution.filters.Filter
import com.intellij.execution.impl.InlayProvider
import com.intellij.java.terminal.shared.JavaTerminalBundle
import com.intellij.java.terminal.shared.JavaTerminalSettings
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.text.CharArrayUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.terminal.hyperlinks.filter.TerminalFilterScope

/** A bunch of binaries that we target. Only common stuff between a jdk8 and jdk24 */
private val EXPECTED_BINARIES: List<String> = listOf(
  //"jar",
  //"jarsigner",
  "java",
  "javac",
  "javadoc",
  "javap",
  //"jcmd",
  //"jconsole",
  //"jdb",
  //"jdeps",
  //"jfr",
  //"jinfo",
  //"jmap",
  //"jps",
  //"jrunscript",
  //"jstack",
  //"jstat",
  //"jstatd",
  //"keytool",
  //"rmiregistry",
  //"serialver",
)

/**
 * Filter suggesting JDK configuration window when the user attempts to use a java command 
 * without a project SDK configured
 */
@VisibleForTesting
@ApiStatus.Internal
class NoJavaExecutableFilter : ConsoleFilterProviderEx, Filter {
  override fun getDefaultFilters(project: Project): Array<out Filter> = emptyArray()

  override fun getDefaultFilters(project: Project, scope: GlobalSearchScope): Array<out Filter> {
    if (scope is TerminalFilterScope
        && JavaTerminalSettings.instance.overrideJavaHome
        && ProjectRootManager.getInstance(project).getProjectSdk() == null) {
      return arrayOf(this)
    }
    return emptyArray()
  }

  override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
    if (isCommandNotFound(line)) {
      return Filter.Result(listOf(
        // Fake item to avoid unwrapping in `CompositeFilter.createFinalResult`
        Filter.ResultItem(0, 0, null),
        InstallSdkResultItem(entireLength - 2, entireLength - 1)))
    }

    return null
  }

  /** 
   * Perform handwritten heuristics to figure out if the line is about a command not found
   * regardless of the locale and shell integration (PowerShell, sh, bash, zsh, fish...)
   */
  private fun isCommandNotFound(line: String): Boolean {
    // most "not found" kind of messages have `:` in their structure
    if (!line.endsWith('\n') || !line.contains(':')) return false

    // bash/fish/zsh: not found
    val shellIndex = CharArrayUtil.indexOf(line, "sh", 0, 4)
    if (shellIndex in 1..<4) {
      for (binary in EXPECTED_BINARIES) {
        if (line.contains(": $binary")) {
          return true
        }
      }

      // Not about our binaries
      return false
    }

    // Less likely path
    for (binary in EXPECTED_BINARIES) {
      val binaryIndex = CharArrayUtil.indexOf(line, binary, shellIndex)
      if (binaryIndex < 0) continue

      // Starfish not found style 
      if (line.startsWith("$binary: ")) return true
      // PowerShell not found style
      if (line.startsWith("$binary : ")) return true
      // The program may be installed with "apt"
      // apparently there are variants depending on the language
      if (line.contains("« ${binary} »")) return true
      if (line.contains("«${binary}»")) return true
      if (line.contains("'${binary}'")) return true
    }

    return false
  }

  /**
   * Small button showing up to guide the user towards the JDK project configuration
   */
  private class InstallSdkResultItem(highlightStart: Int, highlightEnd: Int) : Filter.ResultItem(highlightStart, highlightEnd, null),
                                                                               InlayProvider {
    override fun createInlayRenderer(editor: Editor?): EditorCustomElementRenderer {
      val factory = PresentationFactory(editor!!)
      val inlayButtonFactory = InlayButtonPresentationFactory(editor,
                                                              factory,
                                                              DefaultLanguageHighlighterColors.INLAY_BUTTON_DEFAULT,
                                                              DefaultLanguageHighlighterColors.INLAY_BUTTON_HOVERED,
                                                              DefaultLanguageHighlighterColors.INLAY_BUTTON_FOCUSED)

      val inlayPresentation = inlayButtonFactory.smallText(JavaTerminalBundle.message("inlay.override.jdk")).onClick { _, _ ->
        val project = editor.project ?: return@onClick
        project.putUserData(SHOULD_DISPLAY_NOTIFICATION, true)
        ProjectSettingsService.getInstance(project).openProjectSettings()
      }.build()

      return PresentationRenderer(inlayPresentation)
    }
  }
}
