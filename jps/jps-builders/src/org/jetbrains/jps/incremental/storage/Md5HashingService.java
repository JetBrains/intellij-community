// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.storage;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

class Md5HashingService {
  private static final ThreadLocal<MessageDigest> MESSAGE_DIGEST_THREAD_LOCAL = new ThreadLocal<>();
  private static final String HASH_FUNCTION = "MD5";
  private static final byte CARRIAGE_RETURN_CODE = 13;
  private static final byte LINE_FEED_CODE = 10;
  private static final int HASH_SIZE = 16;

  static byte[] getStringHash(@NotNull String hashableString) throws IOException {
    MessageDigest md = getMessageDigest();
    md.reset();
    //noinspection SSBasedInspection,ImplicitDefaultCharsetUsage
    return md.digest(hashableString.getBytes());
  }

  static byte[] getFileHash(@NotNull File file) throws IOException {
    MessageDigest md = getMessageDigest();
    md.reset();
    try (FileInputStream fis = new FileInputStream(file)) {
      byte[] buffer = new byte[1024 * 1024];
      int length;
      while ((length = fis.read(buffer)) != -1) {
        byte[] result = new byte[length];
        int copiedBytes = 0;
        for (int i = 0; i < length; i++) {
          if (buffer[i] != CARRIAGE_RETURN_CODE && ((i + 1) >= length || buffer[i + 1] != LINE_FEED_CODE)) {
            result[copiedBytes] = buffer[i];
            copiedBytes++;
          }
        }
        md.update(copiedBytes != result.length ? Arrays.copyOf(result, result.length - (result.length - copiedBytes)) : result);
      }
    }
    return md.digest();
  }

  static int getHashSize() {
    return HASH_SIZE;
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
