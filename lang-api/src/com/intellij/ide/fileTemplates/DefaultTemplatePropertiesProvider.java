package com.intellij.ide.fileTemplates;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiDirectory;

import java.util.Properties;

/**
 * Provides default variables which are available in file templates.
 *
 * @author yole
 * @since 8.0
 */
public interface DefaultTemplatePropertiesProvider {
  ExtensionPointName<DefaultTemplatePropertiesProvider> EP_NAME = ExtensionPointName.create("com.intellij.defaultTemplatePropertiesProvider");

  /**
   * Fills the default properties for a file which is created in the specified directory.
   *
   * @param directory the directory in which the file is created.
   * @param props the map in which the defined properties should be stored.
   */
  void fillProperties(PsiDirectory directory, Properties props);
}
