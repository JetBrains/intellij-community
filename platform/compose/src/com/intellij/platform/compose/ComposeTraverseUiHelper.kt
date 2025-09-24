// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.intellij.ide.ui.search.SearchableOptionEntry
import com.intellij.ide.ui.search.TraverseUIHelper
import com.intellij.ide.ui.search.processUiLabel
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ex.ConfigurableWrapper
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.bridge.theme.SwingBridgeTheme
import org.jetbrains.jewel.foundation.ExperimentalJewelApi

@ApiStatus.Internal
class ComposeTraverseUiHelper : TraverseUIHelper {
  @OptIn(ExperimentalComposeUiApi::class, ExperimentalJewelApi::class)
  override fun afterConfigurable(configurable: SearchableConfigurable, options: MutableSet<SearchableOptionEntry>) {
    val originalConfigurable = if (configurable is ConfigurableWrapper) configurable.configurable else configurable
    if (originalConfigurable is ComposeSearchableConfigurable) {
      val imageComposeScene = ImageComposeScene(600, 600)
      try {
        imageComposeScene.setContent {
          SwingBridgeTheme {
            originalConfigurable.ComposeContent()
          }
        }
        imageComposeScene.render(0L)

        imageComposeScene.semanticsOwners.forEach {
          it.rootSemanticsNode.collectSearchableOptions(options)
        }
      }
      finally {
        imageComposeScene.close()
      }
    }
  }


  private fun SemanticsNode.collectSearchableOptions(options: MutableSet<SearchableOptionEntry>) {
    config.getOrNull(SemanticsProperties.Text)?.forEach {
      processUiLabel(
        title = it.text,
        configurableOptions = null,
        path = null,
        i18n = false,
        rawList = options,
      )
    }

    config.getOrNull(SemanticsProperties.EditableText)?.let {
      processUiLabel(
        title = it.text,
        configurableOptions = null,
        path = null,
        i18n = false,
        rawList = options,
      )
    }

    children.forEach { child ->
      child.collectSearchableOptions(options)
    }
  }
}

