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

package com.intellij.history.core.storage;

import com.intellij.openapi.util.io.FileUtil;

import java.io.*;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

public class CompressingContentStorage implements IContentStorage {
  private final IContentStorage mySubject;
  private final Deflater myDeflater = new Deflater(Deflater.BEST_SPEED);
  private final Inflater myInflater = new Inflater();

  public CompressingContentStorage(IContentStorage s) {
    mySubject = s;
  }

  public void save() {
    mySubject.save();
  }

  public void close() {
    mySubject.close();
  }

  public int store(byte[] content) throws BrokenStorageException {
    try {
      ByteArrayOutputStream output = new ByteArrayOutputStream();

      myDeflater.reset();
      OutputStream s = createDeflaterOutputStream(output);
      try {
        s.write(content);
      }
      finally {
        s.close();
        myDeflater.reset();
      }

      return mySubject.store(output.toByteArray());
    }
    catch (IOException e) {
      throw new BrokenStorageException(e);
    }
  }

  public byte[] load(int id) throws BrokenStorageException {
    try {
      ByteArrayOutputStream output = new ByteArrayOutputStream();

      byte[] content = mySubject.load(id);

      myInflater.reset();
      InputStream s = createInflaterOutputStream(content);
      try {
        FileUtil.copy(s, output);
      }
      catch (IOException e) {
        // todo hook for IDEADEV-25408.
        String m = "Failed to copy content. id = " + id + " length=" + content.length;
        IOException newEx = new IOException(m);
        newEx.initCause(e);
        throw newEx;
      } finally {
        s.close();
        myInflater.reset();
      }

      return output.toByteArray();
    }
    catch (IOException e) {
      throw new BrokenStorageException(e);
    }
  }

  protected OutputStream createDeflaterOutputStream(OutputStream output) {
    return new DeflaterOutputStream(output, myDeflater);
  }

  protected InputStream createInflaterOutputStream(byte[] content) {
    return new InflaterInputStream(new ByteArrayInputStream(content), myInflater);
  }

  public void remove(int id) {
    mySubject.remove(id);
  }

  public void setVersion(final int version) {
    mySubject.setVersion(version);
  }

  public int getVersion() {
    return mySubject.getVersion();
  }  
}
