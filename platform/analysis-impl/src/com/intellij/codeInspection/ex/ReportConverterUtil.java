// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.InspectionsReportConverter;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class ReportConverterUtil {
  public static @Nullable InspectionsReportConverter getReportConverter(@Nullable String outputFormat) {
    return InspectionsReportConverter.EP_NAME.getExtensionList().stream()
      .filter(converter -> converter.getFormatName().equals(outputFormat))
      .findFirst().orElse(null);
  }

  @NotNull
  public static Path getResultsDataPath(@NotNull Disposable parentDisposable, @Nullable InspectionsReportConverter reportConverter, @NotNull String outputPath) throws IOException {
    Path resultsDataPath;
    if ((reportConverter == null || !reportConverter.useTmpDirForRawData())
        // use default xml converter(if null( or don't store default xml report in tmp dir
        &&
        outputPath != null) {  // and don't use STDOUT stream
      resultsDataPath = Paths.get(outputPath);
      Files.createDirectories(resultsDataPath);
    }
    else {

      File tmpDir = FileUtilRt.createTempDirectory("inspections", "data", false);
      Disposer.register(parentDisposable, () -> FileUtil.delete(tmpDir));
      resultsDataPath = tmpDir.toPath();
    }
    return resultsDataPath;
  }
}
