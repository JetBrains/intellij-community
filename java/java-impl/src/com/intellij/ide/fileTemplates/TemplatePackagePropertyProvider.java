package com.intellij.ide.fileTemplates;

import com.intellij.psi.PsiDirectory;

import java.util.Properties;

/**
 * @author yole
 */
public class TemplatePackagePropertyProvider implements DefaultTemplatePropertiesProvider {
  public void fillProperties(final PsiDirectory directory, final Properties props) {
    JavaTemplateUtil.setPackageNameAttribute(props, directory);
  }
}
