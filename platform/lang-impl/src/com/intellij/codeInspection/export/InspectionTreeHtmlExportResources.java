// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.export;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * @author Dmitry Batkovich
 */
final class InspectionTreeHtmlExportResources {
  private static final Logger LOG = Logger.getInstance(InspectionTreeHtmlExportResources.class);

  static void copyInspectionReportResources(@NotNull Path targetDirectory) {
    copyInspectionReportResource("styles.css", targetDirectory);
    copyInspectionReportResource("script.js", targetDirectory);
  }

  private static void copyInspectionReportResource(@NotNull String resourceName, @NotNull Path targetDirectory) {
    Path resourceTargetFile = targetDirectory.resolve(resourceName);
    try {
      Files.createDirectories(resourceTargetFile.getParent());
    }
    catch (IOException e) {
      LOG.error("Can't create file: " + resourceTargetFile);
    }

    try (InputStream input = InspectionTreeHtmlExportResources.class.getClassLoader().getResourceAsStream("inspectionReport/" + resourceName)) {
      Files.copy(Objects.requireNonNull(input), resourceTargetFile, StandardCopyOption.REPLACE_EXISTING);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }
}
