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

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public final class StaticAnalysisReportConverter extends JsonInspectionsReportConverter {
  @Override
  public String getFormatName() {
    return "sa";
  }

  @Override
  public void projectData(@NotNull Project project, @Nullable String outputPath) {
    if (outputPath == null) {
      return;
    }
    ProjectDescriptionUtilKt.writeProjectDescription(Paths.get(outputPath).resolve("projectStructure.json"), project);
  }

  public static void writeInspectionsMeta(Path source, Path target) throws ConversionException {
    if (Files.notExists(source)) {
      return;
    }
    try {
      Files.copy(source, target, REPLACE_EXISTING);
    }
    catch (IOException e) {
      throw new ConversionException(e);
    }
  }
}
