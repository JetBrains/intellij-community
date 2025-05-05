// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.bazel.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZipEntryIterator implements Iterator<ZipEntryIterator.StreamEntry> {
  private final ZipInputStream zis;
  private StreamEntry nextEntry;

  public interface StreamEntry {
    ZipEntry getEntry();
    ZipInputStream getStream();
  }

  public ZipEntryIterator(InputStream is) {
    zis = new ZipInputStream(is);
  }

  @Override
  public boolean hasNext() {
    return getNextEntry() != null;
  }

  @Override
  public StreamEntry next() {
    try {
      return getNextEntry();
    }
    finally {
      nextEntry = null;
    }
  }

  private StreamEntry getNextEntry() {
    if (nextEntry == null) {
      try {
        ZipEntry entry = zis.getNextEntry();
        nextEntry = entry == null? null : new StreamEntry() {
          @Override
          public ZipEntry getEntry() {
            return entry;
          }

          @Override
          public ZipInputStream getStream() {
            return zis;
          }
        };
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return nextEntry;
  }
}
