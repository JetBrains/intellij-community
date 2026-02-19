// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl;

import com.dynatrace.hash4j.hashing.HashStream64;
import com.dynatrace.hash4j.hashing.Hashing;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.util.SystemInfo;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/** @noinspection NonFinalUtilityClass*/
public class Utils {

  public static long digest(String str) {
    return Hashing.xxh3_64().hashStream().putString(str).getAsLong();
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

  public static boolean deleteIfExists(Path path) throws IOException {
    try {
      return Files.deleteIfExists(path);
    }
    catch (AccessDeniedException e) {
      return path.toFile().delete(); // fallback in case of readonly attribute
    }
  }

  public static void deleteRecursively(Path dataDir) throws IOException {
    try {
      // this method makes use of deleteIfExists() that can handle cases when standard Files.deleteIfExists(path) fails to delete
      // the file because of AccessDeniedException, which might happen on Windows, where file might remain in a "locked" state for no reason
      Files.walkFileTree(dataDir, new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          deleteIfExists(file);
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
          if (exc != null) {
            throw exc;
          }
          try {
            deleteIfExists(dir);
          }
          catch (DirectoryNotEmptyException ignore) {
          }
          return FileVisitResult.CONTINUE;
        }
      });
    }
    catch (NoSuchFileException ignored) {
    }
  }

  private static final String WINDOWS_ERROR_TOO_MANY_LINKS;
  static {
    // sun.nio.fs.WindowsNativeDispatcher.FormatMessage(1142)
    String errorMessage = "An attempt was made to create more links on a file than the file system supports"; // default
    if (SystemInfo.isWindows) {
      try {
        // attempt to get the locate specific error message
        Method requestMethod = Class.forName("sun.nio.fs.WindowsNativeDispatcher").getDeclaredMethod("FormatMessage", int.class);
        requestMethod.setAccessible(true);
        errorMessage = (String)requestMethod.invoke(null, 1142 /*the code for 'too many hardlinks' error*/);
      }
      catch (Throwable err) {
        throw new RuntimeException(err);
      }
    }
    WINDOWS_ERROR_TOO_MANY_LINKS = errorMessage;
  }

  public static boolean tryCreateLink(Path link, Path existing) throws IOException {
    try {
      Files.createLink(link, existing);
      return true;
    }
    catch (FileSystemException e) {
      String message = e.getMessage();
      if (message != null && message.endsWith(WINDOWS_ERROR_TOO_MANY_LINKS)) {
        return false;
      }
      else {
        throw e;
      }
    }
  }
}
