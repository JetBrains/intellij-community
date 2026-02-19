// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.fileTemplates;

import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.NotNull;

import java.util.Properties;


public class TemplatePackagePropertyProvider implements DefaultTemplatePropertiesProvider {
  @Override
  public void fillProperties(final @NotNull PsiDirectory directory, final @NotNull Properties props) {
    JavaTemplateUtil.setPackageNameAttribute(props, directory);
  }
}
