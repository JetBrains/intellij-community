package org.jetbrains.jps.javac;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.tools.JavaFileObject;
import java.io.*;
import java.net.URI;

/**
* @author Eugene Zhuravlev
*         Date: 1/23/12
*/
class DummyJavaFileObject implements JavaFileObject {
  // todo: use proxy to handle possible interface changes?
  private final URI myUri;

  DummyJavaFileObject(URI uri) {
    myUri = uri;
  }

  public Kind getKind() {
    return Kind.SOURCE;
  }

  public boolean isNameCompatible(String simpleName, Kind kind) {
    throw new UnsupportedOperationException();
  }

  public NestingKind getNestingKind() {
    throw new UnsupportedOperationException();
  }

  public Modifier getAccessLevel() {
    throw new UnsupportedOperationException();
  }

  public URI toUri() {
    return myUri;
  }

  public String getName() {
    throw new UnsupportedOperationException();
  }

  public InputStream openInputStream() throws IOException {
    throw new UnsupportedOperationException();
  }

  public OutputStream openOutputStream() throws IOException {
    throw new UnsupportedOperationException();
  }

  public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
    throw new UnsupportedOperationException();
  }

  public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
    throw new UnsupportedOperationException();
  }

  public Writer openWriter() throws IOException {
    throw new UnsupportedOperationException();
  }

  public long getLastModified() {
    throw new UnsupportedOperationException();
  }

  public boolean delete() {
    throw new UnsupportedOperationException();
  }
}
