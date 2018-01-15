// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.backwardRefs.index;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class CompilerReferenceIndexUtil {
  private static final Logger LOG = Logger.getInstance(CompilerReferenceIndexUtil.class);

  private CompilerReferenceIndexUtil() {}

  public static boolean exists(@NotNull File buildDir, CompilerIndexDescriptor<?> descriptor) {
    return descriptor.getIndicesDir(buildDir).exists();
  }

  public static void removeIndexFiles(@NotNull File buildDir, CompilerIndexDescriptor<?> descriptor) {
    final File indexDir = descriptor.getIndicesDir(buildDir);
    if (indexDir.exists()) {
      FileUtil.delete(indexDir);
    }
  }

  public static boolean versionDiffers(@NotNull File buildDir, CompilerIndexDescriptor<?> descriptor) {
    File versionFile = descriptor.getVersionFile(buildDir);

    try {
      final DataInputStream is = new DataInputStream(new FileInputStream(versionFile));
      try {
        int currentIndexVersion = is.readInt();
        boolean isDiffer = currentIndexVersion != descriptor.getVersion();
        if (isDiffer) {
          LOG.info("backward reference index version differ, expected = " + descriptor.getVersion() + ", current = " + currentIndexVersion);
        }
        return isDiffer;
      }
      finally {
        is.close();
      }
    }
    catch (IOException ignored) {
      LOG.info("backward reference index version differ due to: " + ignored.getClass());
    }
    return true;
  }


  public static boolean existsWithLatestVersion(File buildDir, CompilerIndexDescriptor<?> descriptor) {
    if (buildDir == null || versionDiffers(buildDir, descriptor)) {
      return false;
    }
    return exists(buildDir, descriptor);
  }
}
