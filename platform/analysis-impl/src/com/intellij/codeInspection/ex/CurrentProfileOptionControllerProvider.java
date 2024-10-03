// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.options.OptionController;
import com.intellij.codeInspection.options.OptionControllerProvider;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * {@link OptionControllerProvider} for current profile. Supported bindIds:
 * <ul>
 *   <li>currentProfile.&lt;InspectionShortName&gt;.options.&lt;InspectionOptionId&gt; - option of a given inspection</li>
 * </ul>
 */
@ApiStatus.Internal
public final class CurrentProfileOptionControllerProvider implements OptionControllerProvider {
  @Override
  public @NotNull OptionController forContext(@NotNull PsiElement context) {
    return InspectionProfileManager.getInstance(context.getProject()).getCurrentProfile().controllerFor(context);
  }

  @Override
  public @NotNull String name() {
    return "currentProfile";
  }
}
