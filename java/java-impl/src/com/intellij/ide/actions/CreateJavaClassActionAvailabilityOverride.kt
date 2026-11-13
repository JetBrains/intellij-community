// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiDirectory
import com.intellij.util.ThreeState
import org.jetbrains.annotations.ApiStatus

/**
 * Allows disabling "New -> Java Class" action where it's normally available, or enabling it outside of Java source roots.
 */
@ApiStatus.Internal
public interface CreateJavaClassActionAvailabilityOverride {
  public fun isActionAvailable(directories: Array<out PsiDirectory>): ThreeState

  public companion object {
    private val EP_NAME: ExtensionPointName<CreateJavaClassActionAvailabilityOverride> =
      ExtensionPointName.create("com.intellij.java.createClassActionAvailabilityOverride")

    @JvmStatic
    public fun getAvailabilityOverride(directories: Array<out PsiDirectory>): ThreeState {
      for (extension in EP_NAME.extensionList) {
        val override = extension.isActionAvailable(directories)
        if (override != ThreeState.UNSURE) return override
      }
      return ThreeState.UNSURE
    }
  }
}
