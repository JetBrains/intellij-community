// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.javac;

import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.annotations.NotNull;

import javax.tools.JavaFileManager;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.CharBuffer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ZipFileObject extends JpsFileObject {
  private final ZipFile myZip;
  private final ZipEntry myEntry;
  private final String myEncoding;

  ZipFileObject(File root, ZipFile zip, ZipEntry entry, String encoding, final JavaFileManager.Location location) {
    super(createUri(root, entry.getName()), findKind(entry.getName()), location);
    myZip = zip;
    myEntry = entry;
    myEncoding = encoding;
  }

  @NotNull
  private static URI createUri(final File zipFile, String relPath) {
    final String p = FileUtilRt.toSystemIndependentName(zipFile.getPath());
    final StringBuilder buf = new StringBuilder(p.length() + relPath.length() + 5);
    if (!p.startsWith("/")) {
      buf.append("///");
    }
    else if (!p.startsWith("//")) {
      buf.append("//");
    }
    buf.append(p).append(relPath.startsWith("/") ? "!" : "!/").append(relPath);
    try {
      return new URI("jar", null, buf.toString(), null);
    }
    catch (URISyntaxException e) {
      throw new Error("Cannot create URI " + buf, e);
    }
  }

  @Override
  public InputStream openInputStream() throws IOException {
    return new BufferedInputStream(myZip.getInputStream(myEntry));
  }

  @Override
  public Writer openWriter() throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getLastModified() {
    return myEntry.getTime();
  }

  @Override
  public boolean delete() {
    throw new UnsupportedOperationException();
  }

  @Override
  protected String inferBinaryName(Iterable<? extends File> path, final boolean caseSensitiveFS) {
    final String name = myEntry.getName();
    int idx = name.lastIndexOf(".");
    return (idx < 0 ? name : name.substring(0, idx)).replace('/', '.');
  }


  @Override
  public boolean isNameCompatible(@NotNull String cn, Kind kind) {
    if (kind == Kind.OTHER) {
      return getKind() == kind;
    }
    if (getKind() != kind) {
      return false;
    }
    final String name = myEntry.getName();
    return name.length() == (cn.length() + kind.extension.length()) && name.startsWith(cn) && name.endsWith(kind.extension);
  }

  /**
   * Check if two file objects are equal.
   * Two RegularFileObjects are equal if the absolute paths of the underlying
   * files are equal.
   */
  @Override
  public boolean equals(Object other) {
    return other instanceof ZipFileObject && super.equals(other);
  }

  @Override
  public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
    // todo: consider adding content caching if needed
    // todo: currently ignoreEncodingErrors is not honored. Do we actually need to support it?
    try (InputStream in = openInputStream()) {
      try (InputStreamReader reader = myEncoding != null ? new InputStreamReader(in, myEncoding) : new InputStreamReader(in)) {
        return CharBuffer.wrap(FileUtilRt.loadText(reader, (int)myEntry.getSize()));
      }
    }
  }
}
