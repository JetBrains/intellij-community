// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.storage;

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

final class MurmurHashingService {
  static final int HASH_SIZE = 16;

  static byte[] getStringHash(@NotNull String hashableString) {
    Hasher hasher = Hashing.murmur3_128().newHasher();
    hasher.putString(hashableString, StandardCharsets.UTF_8);
    return hasher.hash().asBytes();
  }

  static byte[] getFileHash(@NotNull File file) throws IOException {
    Hasher hasher = Hashing.murmur3_128().newHasher();
    try (FileInputStream fis = new FileInputStream(file); FileChannel fileChannel = fis.getChannel()) {
      ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024);
      while(fileChannel.read(buffer) > 0) {
        buffer.flip();
        hasher.putBytes(buffer);
        buffer.clear();
      }
    }
    return hasher.hash().asBytes();
  }
}
