// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.perFileVersion;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.IntObjectLRUMap;
import com.intellij.util.io.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public final class PersistentSubIndexerVersionEnumerator<SubIndexerVersion> implements Closeable {
  private static volatile int STORAGE_SIZE_LIMIT = 1024 * 1024;

  private final class MyEnumerator implements DataEnumerator<SubIndexerVersion> {
    @Override
    public synchronized int enumerate(@Nullable SubIndexerVersion value) throws IOException {
      Integer val = myMap.get(value);
      if (val != null){ return val;}
      myMap.put(value, ++myNextVersion);
      if (myNextVersion == Integer.MAX_VALUE) {
        throw new IOException("Request index rebuild");
      }
      return myNextVersion;
    }

    @Override
    public @Nullable SubIndexerVersion valueOf(int idx) {
      throw new UnsupportedOperationException();
    }
  }

  private final @NotNull CachingEnumerator<SubIndexerVersion> myEnumerator;
  private final @NotNull File myFile;
  private final @NotNull KeyDescriptor<SubIndexerVersion> mySubIndexerTypeDescriptor;
  private volatile PersistentHashMap<SubIndexerVersion, Integer> myMap;
  private volatile int myNextVersion;
  private volatile int myWrittenNextVersion;

  // only initialized on first request to valueOf()
  private volatile IntObjectLRUMap<Ref<SubIndexerVersion>> myValueOfCache = null;

  public PersistentSubIndexerVersionEnumerator(@NotNull File file,
                                               @NotNull KeyDescriptor<SubIndexerVersion> subIndexerTypeDescriptor) throws IOException {
    myFile = file;
    mySubIndexerTypeDescriptor = subIndexerTypeDescriptor;
    myEnumerator = new CachingEnumerator<>(new MyEnumerator(), subIndexerTypeDescriptor);
    init();
    if (myNextVersion >= STORAGE_SIZE_LIMIT) {
      throw new IOException("Rebuild index due to attribute version enumerator overflow");
    }
  }

  public int enumerate(SubIndexerVersion version) throws IOException {
    return myEnumerator.enumerate(version);
  }

  /**
   * should not be used in production code, only testing purposes
   */
  public SubIndexerVersion valueOf(int idx) throws IOException {
    if (myValueOfCache == null) {
      synchronized (this) {
        if (myValueOfCache == null) {
          myValueOfCache = new IntObjectLRUMap<>(256);
        }
      }
    }
    synchronized (this) {
      var cached = myValueOfCache.getEntry(idx);
      if (cached != null) return cached.value.get();
    }
    Ref<SubIndexerVersion> ref = new Ref<>();
    myMap.processKeysWithExistingMapping(version -> {
      Integer versionIdx;
      try {
        versionIdx = myMap.get(version);
      }
      catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      if (Comparing.equal(idx, versionIdx)) {
        ref.set(version);
        return false;
      }
      return true;
    });
    synchronized (this) {
      myValueOfCache.putEntry(new IntObjectLRUMap.MapEntry<>(idx, ref));
    }
    return ref.get();
  }

  private void init() throws IOException {
    myMap = PersistentMapBuilder.newBuilder(myFile.toPath(), mySubIndexerTypeDescriptor, EnumeratorIntegerDescriptor.INSTANCE)
      //.wantNonNegativeIntegralValues()
    .build();
      // getSize/remove are required here
    File nextVersionFile = getNextVersionFile(myFile);
    String intValue = nextVersionFile.exists() ? FileUtil.loadFile(nextVersionFile, StandardCharsets.UTF_8) : String.valueOf(1);
    try {
      myNextVersion = Integer.parseInt(intValue);
      myWrittenNextVersion = myNextVersion;
    }
    catch (NumberFormatException e) {
      throw new IOException("Invalid next version format " + intValue);
    }
  }

  public void clear() throws IOException {
    myMap.closeAndClean();
    init();
  }

  public void flush() throws IOException {
    myMap.force();
    writeNextVersion();
  }

  public boolean isDirty() {
    return myMap.isDirty() || myNextVersion != myWrittenNextVersion;
  }

  private void writeNextVersion() throws IOException {
    if (myNextVersion != myWrittenNextVersion) {
      FileUtil.writeToFile(getNextVersionFile(myFile), String.valueOf(myNextVersion));
      myWrittenNextVersion = myNextVersion;
    }
  }

  @Override
  public void close() throws IOException {
    if (!myMap.isClosed()) {
      myMap.close();
    }
    writeNextVersion();
  }

  private static @NotNull File getNextVersionFile(File baseFile) {
    return new File(baseFile.getAbsolutePath() + ".next");
  }

  @TestOnly
  public static void setStorageSizeLimit(int storageSizeLimit) {
    STORAGE_SIZE_LIMIT = storageSizeLimit;
  }

  @TestOnly
  public static int getStorageSizeLimit() {
    return STORAGE_SIZE_LIMIT;
  }
}
