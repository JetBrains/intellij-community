// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage;

import com.intellij.util.lang.Xxh3;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

final class Xxh3HashingService {
  static long getStringHash(@NotNull String hashableString) {
    return Xxh3.hash(hashableString);
  }

  static long getFileHash(@NotNull File file) throws IOException {
    long fileHash;
    try (FileInputStream fis = new FileInputStream(file)) {
      fileHash = Xxh3.hash(fis, (int)file.length());
    }
    return fileHash;
  }

  static long hashLongs(long... values) {
    return Xxh3.hashLongs(values);
  }
}
