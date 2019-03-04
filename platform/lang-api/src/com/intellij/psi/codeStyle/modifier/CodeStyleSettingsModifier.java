// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle.modifier;

import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows to modify current project settings for a specific PSI file. There can be several modifiers applying changes to the same instance
 * of {@code TransientCodeStyleSettings} object. The modifier can be registered via "com.intellij.codeStyleSettingsModifier" extension
 * point.
 */
@ApiStatus.Experimental
public interface CodeStyleSettingsModifier {

  /**
   * Modifies given settings. The modifier may add dependencies to the transient code style settings to update them if the dependencies
   * change, see {@link TransientCodeStyleSettings#addDependency(ModificationTracker)} method.
   *
   * @param settings  The settings to modify, may contain changes made by other code style settings modifiers.
   * @param file      The PSI file for which a modification is to be made.
   * @return True if the modifier has made any changes, false otherwise.
   */
  boolean modifySettings(@NotNull TransientCodeStyleSettings settings, @NotNull PsiFile file);

  /**
   * A factory method which returns status bar UI contributor for the given settings given that the settings have been modified by this
   * specific modifier.
   *
   * @param transientSettings The settings to construct UI contributor for.
   * @return The UI contributor or null if code style changes should not be indicated in the status bar.
   * @see CodeStyleStatusBarUIContributor
   */
  @Nullable
  CodeStyleStatusBarUIContributor getStatusBarUiContributor(@NotNull TransientCodeStyleSettings transientSettings);

}