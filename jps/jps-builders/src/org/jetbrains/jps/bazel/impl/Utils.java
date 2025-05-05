// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.bazel.impl;

import com.dynatrace.hash4j.hashing.HashStream64;
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

  public static long digest(Iterable<String> strStream) {
    HashStream64 stream = Hashing.xxh3_64().hashStream();
    for (String data : strStream) {
      stream.putString(data);
    }
    return stream.getAsLong();
  }

  public static long digestContent(Iterable<byte[]> contentStream) {
    HashStream64 stream = Hashing.xxh3_64().hashStream();
    for (byte[] bytes : contentStream) {
      stream.putByteArray(bytes);
    }
    return stream.getAsLong();
  }

  public static String timestampDigest(File file) {
    return timestampDigest(file.toPath(), null);
  }
  
  public static String timestampDigest(Path path, @Nullable BasicFileAttributes attrs) {
    try {
      return Long.toHexString((attrs != null? attrs.lastModifiedTime().toMillis() : Files.getLastModifiedTime(path).toMillis())); 
    }
    catch (IOException ignored) {
      return "";
    }
  }

}
