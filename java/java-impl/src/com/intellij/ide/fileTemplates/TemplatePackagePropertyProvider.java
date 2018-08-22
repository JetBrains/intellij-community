// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.fileTemplates;

import com.intellij.psi.PsiDirectory;

import java.util.Properties;

/**
 * @author yole
 */
public class TemplatePackagePropertyProvider implements DefaultTemplatePropertiesProvider {
  @Override
  public void fillProperties(final PsiDirectory directory, final Properties props) {
    JavaTemplateUtil.setPackageNameAttribute(props, directory);
  }
}
