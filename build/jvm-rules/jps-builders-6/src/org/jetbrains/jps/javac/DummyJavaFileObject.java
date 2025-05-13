// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.javac;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.tools.*;
import java.io.*;
import java.net.URI;

final class DummyJavaFileObject implements JavaFileObject {
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
