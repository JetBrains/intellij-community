/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.incremental;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

/**
* @author Eugene Zhuravlev
*/
public final class BinaryContent {
  private final byte[] myBuffer;
  private final int myOffset;
  private final int myLength;

  public BinaryContent(byte[] buf) {
    this(buf, 0, buf.length);
  }

  public BinaryContent(byte[] buf, int off, int len) {
    myBuffer = buf;
    myOffset = off;
    myLength = len;
  }

  public byte[] getBuffer() {
    return myBuffer;
  }

  public int getOffset() {
    return myOffset;
  }

  public int getLength() {
    return myLength;
  }

  public byte[] toByteArray() {
    return Arrays.copyOfRange(myBuffer, myOffset, myOffset + myLength);
  }

  public void saveToFile(File file) throws IOException {
    try {
      _writeToFile(file, this);
    }
    catch (IOException e) {
      // assuming the reason is non-existing parent
      final File parentFile = file.getParentFile();
      if (parentFile == null) {
        throw e;
      }
      //noinspection ResultOfMethodCallIgnored
      parentFile.mkdirs();
      // second attempt
      _writeToFile(file, this);
    }
  }

  private static void _writeToFile(final File file, BinaryContent content) throws IOException {
    final OutputStream stream = new FileOutputStream(file);
    try {
      stream.write(content.getBuffer(), content.getOffset(), content.getLength());
    }
    finally {
      stream.close();
    }
  }

}
