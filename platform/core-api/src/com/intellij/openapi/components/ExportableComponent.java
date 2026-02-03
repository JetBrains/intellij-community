
// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @deprecated use {@link com.intellij.openapi.components.State#additionalExportDirectory()}
 */
@Deprecated
public interface ExportableComponent {
  ExtensionPointName<ServiceBean> EXTENSION_POINT = new ExtensionPointName<>("com.intellij.exportable");

  File @NotNull [] getExportFiles();

  @NotNull
  @Nls(capitalization = Nls.Capitalization.Title)
  String getPresentableName();
}
