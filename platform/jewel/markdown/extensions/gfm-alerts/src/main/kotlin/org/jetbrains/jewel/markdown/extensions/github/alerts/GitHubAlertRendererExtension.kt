// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.markdown.extensions.github.alerts

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.markdown.extensions.MarkdownBlockRendererExtension
import org.jetbrains.jewel.markdown.extensions.MarkdownRendererExtension
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling

@ApiStatus.Experimental
@ExperimentalJewelApi
public class GitHubAlertRendererExtension(alertStyling: AlertStyling, rootStyling: MarkdownStyling) :
    MarkdownRendererExtension {
    override val blockRenderer: MarkdownBlockRendererExtension = GitHubAlertBlockRenderer(alertStyling, rootStyling)
}
