// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import java.io.File;
import java.util.Collections;

public final class FileSearchUtil {
  public static Query<File> findFileRecursively(final File parentDirectory, final String fileName, final int levels, final long timeout) {
    if (parentDirectory == null) return new EmptyQuery<>();
    final QueryExecutor<File, String> executor = (queryParameters, consumer) -> {
      final long endTime = timeout < 0 ? -1L : System.currentTimeMillis() + timeout;
      return processFileRecursively(parentDirectory.getAbsoluteFile(), 1, levels, file -> {
        if (endTime > 0 && System.currentTimeMillis() > endTime) return false;
        if (file.getName().equals(fileName)) {
          if (!consumer.process(file)) return false;
        }
        return true;
      });
    };
    return new ExecutorsQuery<>(fileName, Collections.singletonList(executor));
  }

  private static boolean processFileRecursively(final File parentDirectory, final int curLevel, final int maxLevel,
                                                final Processor<? super File> processor) {
    final File[] files = parentDirectory.listFiles();
    if (files != null) {
      for (File file : files) {
        if (file.isDirectory()) {
          if (curLevel < maxLevel) {
            if (!processFileRecursively(file, curLevel + 1, maxLevel, processor)) {
              return false;
            }
          }
        }
        else if (!processor.process(file)) {
          return false;
        }
      }
    }
    return true;
  }
}
