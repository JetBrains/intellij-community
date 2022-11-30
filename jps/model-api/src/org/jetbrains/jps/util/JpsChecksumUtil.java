// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.util;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class JpsChecksumUtil {
  public static String getSha256Checksum(@NotNull Path path) throws IOException {
    try {
      MessageDigest algorithm = MessageDigest.getInstance("SHA-256");
      return getFileDigest(algorithm, path);
    }
    catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  private static String getFileDigest(@NotNull MessageDigest digest, @NotNull Path file) throws IOException {
    var buf = new byte[65536];
    digest.reset();
    try (var stream = Files.newInputStream(file)) {
      while (true) {
        int count = stream.read(buf);
        if (count <= 0) break;
        digest.update(buf, 0, count);
      }
    }
    return byteArrayToHexString(digest.digest());
  }

  private static String byteArrayToHexString(byte @NotNull [] bytes) {
    StringBuilder builder = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      builder.append(String.format("%02x", b));
    }
    return builder.toString();
  }
}
