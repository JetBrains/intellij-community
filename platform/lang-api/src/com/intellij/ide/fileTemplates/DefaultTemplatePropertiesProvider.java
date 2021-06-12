// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.fileTemplates;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.NotNull;

import java.util.Properties;

/**
 * Provides default variables which are available in file templates.
 */
public interface DefaultTemplatePropertiesProvider {
  ExtensionPointName<DefaultTemplatePropertiesProvider> EP_NAME = ExtensionPointName.create("com.intellij.defaultTemplatePropertiesProvider");

  /**
   * Fills the default properties for a file which is created in the specified directory.
   *
   * @param directory the directory in which the file is created.
   * @param props the map in which the defined properties should be stored.
   */
  void fillProperties(@NotNull PsiDirectory directory, @NotNull Properties props);
}
