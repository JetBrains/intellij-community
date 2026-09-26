// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.fileTemplates;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.NotNull;

import java.util.Properties;

/**
 * Provides default properties which are available in file templates.
 *
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/providing-file-templates.html#providing-default-file-template-properties">Providing Default File Template Properties (IntelliJ Platform Docs)</a>
 */
public interface DefaultTemplatePropertiesProvider {
  ExtensionPointName<DefaultTemplatePropertiesProvider> EP_NAME = ExtensionPointName.create("com.intellij.defaultTemplatePropertiesProvider");

  /**
   * Fills the default properties for a file which is created in the specified directory.
   *
   * @param directory the directory in which the file is created
   * @param props the map in which the defined properties should be stored
   */
  void fillProperties(@NotNull PsiDirectory directory, @NotNull Properties props);
}
