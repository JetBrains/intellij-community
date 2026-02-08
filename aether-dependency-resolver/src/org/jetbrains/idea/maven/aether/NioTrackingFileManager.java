// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.aether;

import org.eclipse.aether.internal.impl.TrackingFileManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

class NioTrackingFileManager implements TrackingFileManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(NioTrackingFileManager.class);

  @Override
  public Properties read(@NotNull File file) {
    synchronized (getMutex(file)) {
      return read(file.toPath());
    }
  }

  @Override
  public Properties update(@NotNull File file, @NotNull Map<@NotNull String, @Nullable String> updates) {
    synchronized (getMutex(file)) {
      return update(file.toPath(), updates);
    }
  }

  private static @Nullable Properties read(@NotNull Path path) {
    if (!Files.isReadable(path)) {
      return null;
    }
    try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.ISO_8859_1)) {
      final Properties properties = new Properties();
      properties.load(reader);
      return properties;
    }
    catch (IOException e) {
      LOGGER.warn("Failed to read tracking file '{}'", path, e);
      throw new UncheckedIOException(e);
    }
  }

  private static @NotNull Properties update(@NotNull Path path, @NotNull Map<@NotNull String, @Nullable String> updates) {
    Properties properties = getUpdatedProperties(path, updates);
    try {
      Files.createDirectories(path.getParent());
    }
    catch (IOException e) {
      LOGGER.warn("Failed to create tracking file parent '{}'", path, e);
      throw new UncheckedIOException(e);
    }
    try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.ISO_8859_1)) {
      properties.store(writer, null);
    }
    catch (IOException e) {
      LOGGER.warn("Failed to write tracking file '{}'", path, e);
    }
    return properties;
  }

  private static @NotNull Properties getUpdatedProperties(@NotNull Path path, @NotNull Map<@NotNull String, @Nullable String> updates) {
    Properties properties = Objects.requireNonNullElseGet(read(path), () -> new Properties());
    if (updates.isEmpty()) {
      return properties;
    }
    for (Map.Entry<String, String> update : updates.entrySet()) {
      if (update.getValue() == null) {
        properties.remove(update.getKey());
      } else {
        properties.setProperty(update.getKey(), update.getValue());
      }
    }
    return properties;
  }

  // copied from org.eclipse.aether.internal.impl.DefaultTrackingFileManager.getMutex
  private static @NotNull Object getMutex(@NotNull File file) {
    // The interned string of path is (mis)used as mutex, to exclude different threads going for same file,
    // as JVM file locking happens on JVM not on Thread level. This is how original code did it  ¯\_(ツ)_/¯
    /*
     * NOTE: Locks held by one JVM must not overlap and using the canonical path is our best bet, still another
     * piece of code might have locked the same file (unlikely though) or the canonical path fails to capture file
     * identity sufficiently as is the case with Java 1.6 and symlinks on Windows.
     */
    try {
      return file.getCanonicalPath().intern();
    }
    catch (IOException e) {
      LOGGER.warn("Failed to canonicalize path {}", file, e);
      // TODO This is code smell and deprecated
      return file.getAbsolutePath().intern();
    }
  }
}
