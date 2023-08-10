// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.fileTemplates;

import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.NotNull;

import java.util.Properties;


public class TemplatePackagePropertyProvider implements DefaultTemplatePropertiesProvider {
  @Override
  public void fillProperties(@NotNull final PsiDirectory directory, @NotNull final Properties props) {
    JavaTemplateUtil.setPackageNameAttribute(props, directory);
  }
}
