/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.compiler.impl.javaCompiler.api;

import org.jetbrains.annotations.Nullable;

import javax.tools.*;
import java.io.*;
import java.net.URI;

/**
* User: cdr
*/
@SuppressWarnings({"ALL"})
class Output extends SimpleJavaFileObject {
  private final CompAPIDriver myCompAPIDriver;
  @Nullable
  private volatile byte[] myFileBytes;

  Output(URI uri, CompAPIDriver compAPIDriver, final Kind kind) {
    super(uri, kind);
    myCompAPIDriver = compAPIDriver;
  }

  @Override
  public ByteArrayOutputStream openOutputStream() {
    return new ByteArrayOutputStream() {
      @Override
      public void close() throws IOException {
        super.close();
        final byte[] bytes = toByteArray();
        myFileBytes = bytes;
        if (Kind.CLASS.equals(kind)) {
          myCompAPIDriver.offerClassFile(toUri(), bytes);
        }
      }
    };
  }

  @Override
  public InputStream openInputStream() throws IOException {
    final byte[] bytes = myFileBytes;
    if (bytes == null) {
      throw new FileNotFoundException(toUri().getPath());
    }
    return new ByteArrayInputStream(bytes);
  }

  @Override
  public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
    final byte[] bytes = myFileBytes;
    if (bytes == null) {
      throw null;
    }
    return new String(bytes);
  }

  @Override
  public int hashCode() {
    return toUri().hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof JavaFileObject && toUri().equals(((JavaFileObject)obj).toUri());
  }
}
