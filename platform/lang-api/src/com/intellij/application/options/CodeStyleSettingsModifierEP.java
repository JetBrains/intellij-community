// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options;

import com.intellij.application.options.CodeStyleSettingsModifier.DependencyList;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;

public class CodeStyleSettingsModifierEP {
  public final static ExtensionPointName<CodeStyleSettingsModifier> EP_NAME =
    ExtensionPointName.create("com.intellij.codeStyleSettingsModifier");

  public static DependencyList modifySettings(@NotNull CodeStyleSettings baseSettings, @NotNull PsiFile file) {
    DependencyList dependencyList = new DependencyList();
    for (CodeStyleSettingsModifier modifier : EP_NAME.getExtensionList()) {
      dependencyList.add(modifier.modifySettings(baseSettings, file));
    }
    return dependencyList;
  }
}
