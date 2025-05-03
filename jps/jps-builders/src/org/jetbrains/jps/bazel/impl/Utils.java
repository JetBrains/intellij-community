// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.bazel.impl;

import com.dynatrace.hash4j.hashing.Hashing;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/** @noinspection NonFinalUtilityClass*/
public class Utils {

  public static String digest(byte[] bytes) {
    return Long.toHexString(Hashing.xxh3_64().hashBytesToLong(bytes));
  }

  public static String digest(File file) {
    return digest(file.toPath(), null);
  }
  
  public static String digest(Path path, @Nullable BasicFileAttributes attrs) {
    try {
      return Long.toHexString((attrs != null? attrs.lastModifiedTime().toMillis() : Files.getLastModifiedTime(path).toMillis())); // todo
    }
    catch (IOException ignored) {
      return "";
    }
  }

}
