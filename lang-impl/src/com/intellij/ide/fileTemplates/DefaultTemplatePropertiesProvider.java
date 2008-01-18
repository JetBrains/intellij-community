package com.intellij.ide.fileTemplates;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiDirectory;

import java.util.Properties;

/**
 * @author yole
 */
public interface DefaultTemplatePropertiesProvider {
  ExtensionPointName<DefaultTemplatePropertiesProvider> EP_NAME = ExtensionPointName.create("com.intellij.defaultTemplatePropertiesProvider");

  void fillProperties(PsiDirectory directory, Properties props);
}
