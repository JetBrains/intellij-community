// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * An abstraction over both {@link java.util.zip.ZipFile} and {@link com.intellij.util.io.zip.JBZipFile}.
 * <p>
 * JDK's ZipFile is highly-performant in local scenario due to native support and general maturity.
 * <p>
 * JBZipFile is adapted to the scenario when a Zip archive is located remotely, and it minimizes the number of IO calls.
 * <p>
 * API visibility note: The users are welcome to deal with {@link ZipHandler}, and the platform will automatically choose the suitable approach
 * based on the file's location. It is not intended that the users could pass their own implementations of {@link GenericZipFile}
 */
@ApiStatus.Internal
public interface GenericZipFile {
  @Nullable GenericZipEntry getEntry(@NotNull String entryName) throws IOException;

  @NotNull List<? extends GenericZipEntry> getEntries() throws IOException;

  void close() throws IOException;

  interface GenericZipEntry {
    long getSize();

    @NotNull String getName();

    long getCrc();

    boolean isDirectory();

    @Nullable InputStream getInputStream() throws IOException;
  }
}
