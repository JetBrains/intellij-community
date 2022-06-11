// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.tasks;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.io.FileExistsException;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.util.HashSet;

public class NoDuplicateZipArchiveOutputStream extends ZipArchiveOutputStream {
  private final HashSet<String> entries = new HashSet<>();

  public NoDuplicateZipArchiveOutputStream(SeekableByteChannel channel) throws IOException {
    super(channel);
  }

  @Override
  public void putArchiveEntry(ArchiveEntry archiveEntry) throws IOException {
    if (!entries.add(archiveEntry.getName())) {
      throw new FileExistsException("File " + archiveEntry.getName() + " already exists");
    }
    super.putArchiveEntry(archiveEntry);
  }
}