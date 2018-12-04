// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options;

import com.intellij.application.options.CodeStyleSettingsModifier.DependencyList;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSource;
import com.intellij.psi.codeStyle.CodeStyleStatusUIContributor;
import com.intellij.psi.codeStyle.CompositeCodeStyleSource;
import com.intellij.psi.codeStyle.TransientCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

public class CodeStyleSettingsModifierEP {
  public final static ExtensionPointName<CodeStyleSettingsModifier> EP_NAME =
    ExtensionPointName.create("com.intellij.codeStyleSettingsModifier");

  public static DependencyList modifySettings(@NotNull TransientCodeStyleSettings transientSettings, @NotNull PsiFile file) {
    DependencyList dependencyList = new DependencyList();
    CompositeCodeStyleSource compositeCodeStyleSource = new CompositeCodeStyleSource();
    for (CodeStyleSettingsModifier modifier : EP_NAME.getExtensionList()) {
      final CodeStyleSettingsModifier.Dependencies dependencies = modifier.modifySettings(transientSettings, file);
      if (dependencies != CodeStyleSettingsModifier.UNMODIFIED) {
        dependencyList.add(dependencies);
        compositeCodeStyleSource.addSource(modifier);
      }
    }
    transientSettings.setSource(compositeCodeStyleSource);
    return dependencyList;
  }
}
