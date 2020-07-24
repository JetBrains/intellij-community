// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.ProjectDescriptionUtilKt;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

final class StaticAnalysisReportConverter extends JsonInspectionsReportConverter {
  @Override
  public String getFormatName() {
    return "sa";
  }

  @Override
  public void projectData(@NotNull Project project, @Nullable String outputPath) throws ConversionException {
    if (outputPath == null) {
      return;
    }
    ProjectDescriptionUtilKt.writeProjectDescription(Paths.get(outputPath).resolve("projectStructure.json"), project);
    writeInspectionsMeta(Paths.get(outputPath).resolve("inspectionsMeta.json"));
  }

  private static void writeInspectionsMeta(Path target) throws ConversionException {
    String path = System.getProperty("inspection.external.metafile.path");
    if (path == null) {
      return;
    }
    Path meta = Paths.get(path);
    if (Files.notExists(meta)) {
      return;
    }
    try {
      Files.copy(meta, target);
    }
    catch (IOException e) {
      throw new ConversionException(e);
    }
  }
}
