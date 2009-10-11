/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
