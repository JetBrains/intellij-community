package com.intellij.openapi.components;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author yole
 */
public interface ExportableComponent {
  ExtensionPointName<ExportableBean> EXTENSION_POINT = new ExtensionPointName<ExportableBean>("com.intellij.exportable");

  @NotNull
  File[] getExportFiles();

  @NotNull
  String getPresentableName();
}
