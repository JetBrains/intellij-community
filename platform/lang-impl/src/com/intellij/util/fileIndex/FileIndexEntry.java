/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.fileIndex;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * @author nik
 */
public class FileIndexEntry {
  private final long myTimeStamp;

  public FileIndexEntry(final long timestamp) {
    myTimeStamp = timestamp;
  }

  public FileIndexEntry(final DataInputStream stream) throws IOException {
    myTimeStamp = stream.readLong();
  }

  public final long getTimeStamp() {
    return myTimeStamp;
  }

  public void write(DataOutputStream stream) throws IOException {
    stream.writeLong(myTimeStamp);
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final FileIndexEntry that = (FileIndexEntry)o;

    if (myTimeStamp != that.myTimeStamp) return false;

    return true;
  }

  public int hashCode() {
    return (int)(myTimeStamp ^ (myTimeStamp >>> 32));
  }
}
