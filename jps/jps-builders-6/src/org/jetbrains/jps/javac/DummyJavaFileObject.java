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
package org.jetbrains.jps.javac;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.tools.*;
import java.io.*;
import java.net.URI;

/**
* @author Eugene Zhuravlev
*/
class DummyJavaFileObject implements JavaFileObject {
  // todo: use proxy to handle possible interface changes?
  private final URI myUri;

  DummyJavaFileObject(URI uri) {
    myUri = uri;
  }

  @Override
  public Kind getKind() {
    return Kind.SOURCE;
  }

  @Override
  public boolean isNameCompatible(String simpleName, Kind kind) {
    throw new UnsupportedOperationException();
  }

  @Override
  public NestingKind getNestingKind() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Modifier getAccessLevel() {
    throw new UnsupportedOperationException();
  }

  @Override
  public URI toUri() {
    return myUri;
  }

  @Override
  public String getName() {
    throw new UnsupportedOperationException();
  }

  @Override
  public InputStream openInputStream() throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public OutputStream openOutputStream() throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Writer openWriter() throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getLastModified() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean delete() {
    throw new UnsupportedOperationException();
  }
}
