// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import androidx.compose.ui.graphics.vector.ImageVector
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface WelcomeRightTabContentProvider {
  // Use Valkyrie IDEA plugin to generate the ImageVector
  fun getBackgroundImageVectorLight(): ImageVector
  fun getBackgroundImageVectorDark(): ImageVector

  val title: String
  val secondaryTitle: String

  companion object {
    private val EP_NAME: ExtensionPointName<WelcomeRightTabContentProvider> = ExtensionPointName("com.intellij.platform.ide.nonModalWelcomeScreen.welcomeScreenContentProvider")

    fun getSingleExtension(): WelcomeRightTabContentProvider? {
      val providers = EP_NAME.extensionList
      if (providers.isEmpty()) return null
      if (providers.size > 1) {
        thisLogger().warn("Multiple WelcomeRightTabContentProvider extensions")
        return null
      }
      return providers.first()
    }
  }
}
