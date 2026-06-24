// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import androidx.compose.runtime.Composable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.jewel.ui.icon.PathIconKey
import java.util.function.Supplier

@ApiStatus.Internal
interface WelcomeRightCustomTabProvider {
  val customSubtitle: Supplier<@Nls String>?
  val tabTitle: Supplier<@Nls String>
  val icon: PathIconKey

  fun isEnabled(): Boolean = true

  @Composable
  fun TabContent(project: Project)

  companion object {
    private val EP_NAME: ExtensionPointName<WelcomeRightCustomTabProvider> = ExtensionPointName("com.intellij.platform.ide.welcomeScreenCustomTabProvider")

    fun getExtensions(): List<WelcomeRightCustomTabProvider> {
      return EP_NAME.extensionList.filter { it.isEnabled() }
    }
  }
}
