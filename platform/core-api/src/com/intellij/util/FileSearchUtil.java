// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.vfs.VirtualFile;

import java.util.Collections;

public final class FileSearchUtil {
  public static Query<VirtualFile> findFileRecursively(final VirtualFile parentDirectory, final String fileName, final int levels, final long timeout) {
    if (parentDirectory == null) return new EmptyQuery<>();
    final QueryExecutor<VirtualFile, String> executor = (queryParameters, consumer) -> {
      final long endTime = timeout < 0 ? -1L : System.currentTimeMillis() + timeout;
      return processFileRecursively(parentDirectory, 1, levels, file -> {
        if (endTime > 0 && System.currentTimeMillis() > endTime) return false;
        if (file.getName().equals(fileName)) {
          if (!consumer.process(file)) return false;
        }
        return true;
      });
    };
    return new ExecutorsQuery<>(fileName, Collections.singletonList(executor));
  }

  private static boolean processFileRecursively(final VirtualFile parentDirectory, final int curLevel, final int maxLevel,
                                                final Processor<? super VirtualFile> processor) {
    final VirtualFile[] files = parentDirectory.getChildren();
    if (files != null) {
      for (VirtualFile file : files) {
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
