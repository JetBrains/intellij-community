// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.javac;

import org.jetbrains.annotations.ApiStatus;

import javax.tools.*;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;

@ApiStatus.Internal
final class ExtInputFileObject extends JpsFileObject {
  private final String myPath;
  private final String myEncoding;
  private final byte[] myContent;

  ExtInputFileObject(JavaFileManager.Location location, String path, String encoding, byte[] content) {
    super(createURI(path), findKind(path), location);
    myPath = path.replace(File.separatorChar, '/');
    myEncoding = encoding;
    myContent = content;
  }

  private static URI createURI(String path) {
    try {
      if (File.separatorChar != '/') path = path.replace(File.separatorChar, '/');
      if (!path.startsWith("/")) path = '/' + path;
      if (path.startsWith("//")) path = "//" + path;
      return new URI("ext-file", null, path, null);
    }
    catch (URISyntaxException e) {
      throw new IllegalArgumentException(path, e);
    }
  }

  @Override
  public InputStream openInputStream() {
    return new ByteArrayInputStream(myContent);
  }

  @Override
  public Writer openWriter() {
    throw new UnsupportedOperationException();
  }

  @Override
  protected String inferBinaryName(Iterable<? extends File> path, final boolean caseSensitiveFS) {
    String _path = myPath;
    int idx = _path.lastIndexOf('.');
    if (idx >= 0) {
      _path = _path.substring(0, idx);
    }
    return _path.replace('/', '.');
  }

  /**
   * Check if two file objects are equal.
   * Two RegularFileObjects are equal if the absolute paths of the underlying
   * files are equal.
   */
  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof ExtInputFileObject)) {
      return false;
    }
    final ExtInputFileObject o = (ExtInputFileObject)other;
    return myPath.equals(o.myPath);
  }

  @Override
  public int hashCode() {
    return myPath.hashCode();
  }

  @Override
  public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
    return new String(myContent, myEncoding);
  }
}
