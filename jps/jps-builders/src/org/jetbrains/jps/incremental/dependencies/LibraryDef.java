// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.dependencies;

import com.intellij.openapi.util.text.StringUtilRt;
import org.jetbrains.jps.dependency.NodeSource;

import java.nio.file.Path;

public final class LibraryDef {
  private LibraryDef() {
  }

  public static boolean isLibraryPath(String path) {
    return StringUtilRt.endsWithIgnoreCase(path, ".jar") || StringUtilRt.endsWithIgnoreCase(path, ".zip");
  }

  public static boolean isLibraryPath(NodeSource src) {
    return isLibraryPath(src.toString());
  }

  public static boolean isLibraryPath(Path path) {
    return isLibraryPath(path.getFileName().toString());
  }

  public static boolean isClassFile(String path) {
    return StringUtilRt.endsWithIgnoreCase(path, ".class");
  }
}
