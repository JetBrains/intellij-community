// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.storage;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

class Md5HashingService {
  private static final ThreadLocal<MessageDigest> MESSAGE_DIGEST_THREAD_LOCAL = new ThreadLocal<>();
  private static final String HASH_FUNCTION = "MD5";
  static final int HASH_SIZE = 16;

  static byte[] getStringHash(@NotNull String hashableString) throws IOException {
    MessageDigest md = getMessageDigest();
    md.reset();
    return md.digest(hashableString.getBytes(StandardCharsets.UTF_8));
  }

  static byte[] getFileHash(@NotNull File file) throws IOException {
    MessageDigest md = getMessageDigest();
    md.reset();
    try (FileInputStream fis = new FileInputStream(file)) {
      byte[] buffer = new byte[1024 * 1024];
      while (fis.read(buffer) != -1) {
        md.update(buffer);
      }
    }
    return md.digest();
  }

  @NotNull
  private static MessageDigest getMessageDigest() throws IOException {
    MessageDigest messageDigest = MESSAGE_DIGEST_THREAD_LOCAL.get();
    if (messageDigest != null) return messageDigest;
    try {
      messageDigest = MessageDigest.getInstance(HASH_FUNCTION);
      MESSAGE_DIGEST_THREAD_LOCAL.set(messageDigest);
      return messageDigest;
    }
    catch (NoSuchAlgorithmException e) {
      throw new IOException(e);
    }
  }
}
