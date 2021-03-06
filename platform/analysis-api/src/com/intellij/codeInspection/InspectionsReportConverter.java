// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInspection.ex.Tools;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * @author Roman.Chernyatchik
 */
public interface InspectionsReportConverter {
  ExtensionPointName<InspectionsReportConverter> EP_NAME = ExtensionPointName.create("com.intellij.inspectionsReportConverter");

  /**
   * @return Format name which will be specified by user using --format option
   */
  String getFormatName();

  /**
   * @return Try if original xml base report isn't required to user and should be stored in tmp directory.
   */
  boolean useTmpDirForRawData();

  /**
   * @param rawDataDirectoryPath Original XML report folder
   * @param outputPath New report output path provided by user. If null use STDOUT.
   * @param tools Inspections data
   * @param inspectionsResults Files with inspection results
   */
  void convert(@NotNull String rawDataDirectoryPath,
               @Nullable String outputPath,
               @NotNull Map<String, Tools> tools,
               @NotNull List<? extends File> inspectionsResults) throws ConversionException;

  default void projectData(@NotNull Project project, @NotNull Path outputPath) {}

  final class ConversionException extends Exception {
    public ConversionException(String message) {
      super(message);
    }

    public ConversionException(Throwable cause) {
      super(cause);
    }

    public ConversionException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
