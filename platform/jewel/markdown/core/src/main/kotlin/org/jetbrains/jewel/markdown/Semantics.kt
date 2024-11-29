package org.jetbrains.jewel.markdown

import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver

public val RawMarkdown: SemanticsPropertyKey<String> = SemanticsPropertyKey("RawMarkdown")
public var SemanticsPropertyReceiver.rawMarkdown: String by RawMarkdown
