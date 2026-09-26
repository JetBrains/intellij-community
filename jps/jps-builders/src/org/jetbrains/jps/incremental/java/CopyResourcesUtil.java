// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.java;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class CopyResourcesUtil {
  private CopyResourcesUtil() {
  }

  public static File copyClass(final String targetPath, final @NonNls String className, final boolean deleteOnExit) throws IOException{
    final File targetDir = new File(targetPath).getAbsoluteFile();
    final File file = new File(targetDir, className + ".class");
    FileUtil.createParentDirs(file);
    if (deleteOnExit) {
      for (File f = file; f != null && !FileUtil.filesEqual(f, targetDir); f = FileUtilRt.getParentFile(f)) {
        f.deleteOnExit();
      }
    }
    final @NonNls String resourceName = "/" + className + ".class";
    final InputStream stream = CopyResourcesUtil.class.getResourceAsStream(resourceName);
    if (stream == null) {
      throw new IOException("cannot load " + resourceName);
    }
    return copyStreamToFile(stream, file);
  }

  private static File copyStreamToFile(final InputStream stream, final File file) throws IOException {
    try (InputStream inputStream = stream;
         FileOutputStream outputStream = new FileOutputStream(file)) {
      FileUtil.copy(inputStream, outputStream);
    }
    return file;
  }

  public static void copyProperties(final String targetPath, final String fileName) throws IOException {
    final File targetDir = new File(targetPath).getAbsoluteFile();
    final File file = new File(targetDir, fileName);
    FileUtil.createParentDirs(file);
    for (File f = file; f != null && !FileUtil.filesEqual(f, targetDir); f = FileUtilRt.getParentFile(f)) {
      f.deleteOnExit();
    }
    final String resourceName = "/" + fileName;
    final InputStream stream = CopyResourcesUtil.class.getResourceAsStream(resourceName);
    if (stream == null) {
      return;
    }
    copyStreamToFile(stream, file);
  }

  public static @NotNull @Unmodifiable List<File> copyFormsRuntime(String targetDir, boolean deleteOnExit) throws IOException {
    String[] runtimeClasses = {
      "AbstractLayout",
      "DimensionInfo",
      "GridConstraints",
      "GridLayoutManager",
      "HorizontalInfo",
      "LayoutState",
      "Spacer",
      "SupportCode$TextWithMnemonic",
      "SupportCode",
      "Util",
      "VerticalInfo",
    };

    List<File> copied = new ArrayList<>();
    for (String runtimeClass : runtimeClasses) {
      copied.add(copyClass(targetDir, "com/intellij/uiDesigner/core/" + runtimeClass, deleteOnExit));
    }
    return copied;
  }
}
