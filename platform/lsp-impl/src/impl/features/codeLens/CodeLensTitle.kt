// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl.features.codeLens

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.NlsSafe
import javax.swing.Icon

/** A code lens title split into the display text and an optional icon. */
internal class CodeLensTitle(@NlsSafe val text: String, val icon: Icon?)

private val CODICON_TOKEN = Regex("""\$\(([\w-]+)\) ?""")

private val CODICON_ICONS: Map<String, Icon> = mapOf(
  "play" to AllIcons.Actions.Execute,
  "run" to AllIcons.Actions.Execute,
  "debug" to AllIcons.Actions.StartDebugger,
  "debug-alt" to AllIcons.Actions.StartDebugger,
)

/**
 * Parses the VS Code codicon syntax in a code lens title, for example `$(play) Run`.
 * The first known codicon becomes the icon.
 * Every codicon token is removed from the text.
 */
internal fun parseCodeLensTitle(title: String): CodeLensTitle {
  if (!title.contains("$(")) return CodeLensTitle(title, null)
  var icon: Icon? = null
  val text = CODICON_TOKEN.replace(title) { match ->
    if (icon == null) icon = CODICON_ICONS[match.groupValues[1]]
    ""
  }.trim()
  if (text.isEmpty() && icon == null) return CodeLensTitle(title, null)
  return CodeLensTitle(text, icon)
}
