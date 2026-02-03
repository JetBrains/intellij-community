// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental;

import com.dynatrace.hash4j.hashing.HashSink;
import com.dynatrace.hash4j.hashing.Hashing;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.text.StringUtilRt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.storage.ProjectStamps;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@ApiStatus.Internal
public final class FileHashUtil {
  /**
   * @param path a normalized system-independent path, possibly relative, but without "." and ".." relative references
   * @param hash hash sink to be updated
   */
  public static void computePathHashCode(@Nullable String path, @NotNull HashSink hash) {
    int length = path == null? 0 : path.length();
    if (length == 0) {
      hash.putInt(0);
    }
    else if (ProjectStamps.PORTABLE_CACHES || SystemInfoRt.isFileSystemCaseSensitive) {
      hash.putString(path);
    }
    else {
      for (int idx = 0; idx < length; idx++) {
        hash.putChar(StringUtilRt.toLowerCase(path.charAt(idx)));
      }
      hash.putInt(length);
    }
  }

  public static long getFileHash(Path file) throws IOException {
    var hash = Hashing.komihash5_0().hashStream();
    getFileHash(file, hash);
    return hash.getAsLong();
  }

  public static void getFileHash(Path file, HashSink hash) throws IOException {
    try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
      long fileSize = channel.size();
      ByteBuffer buffer = ByteBuffer.allocate(256 * 1024);
      long offset = 0L;
      while (offset < fileSize) {
        buffer.clear();
        int readBytes = channel.read(buffer, offset);
        if (readBytes > 0) {
          hash.putBytes(buffer.array(), 0, readBytes);
          offset += readBytes;
        }
        else {
          break;
        }
      }
      hash.putLong(fileSize);
    }
  }
}
