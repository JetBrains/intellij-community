// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage;

import com.intellij.util.lang.Xxh3;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

final class Xxh3HashingService {
  static long getFileHash(@NotNull File file) throws IOException {
    long fileHash;
    try (FileInputStream fis = new FileInputStream(file)) {
      fileHash = Xxh3.hash(fis, Math.toIntExact(file.length()));
    }
    return fileHash;
  }

  static long getFileHash(@NotNull Path file) throws IOException {
    long fileHash;
    try (InputStream fis = Files.newInputStream(file)) {
      fileHash = Xxh3.hash(fis, Math.toIntExact(Files.size(file)));
    }
    return fileHash;
  }
}
