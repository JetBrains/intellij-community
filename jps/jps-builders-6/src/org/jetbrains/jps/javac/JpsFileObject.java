// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.javac;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.CharArrayCharSequence;

import javax.tools.JavaFileManager;
import javax.tools.SimpleJavaFileObject;
import java.io.*;
import java.net.URI;
import java.nio.CharBuffer;

/**
 * @author Eugene Zhuravlev
 * Date: 16-Oct-18
 */
public abstract class JpsFileObject extends SimpleJavaFileObject {
  private static final Kind[] ourAvailableKinds = Kind.values();
  private final JavaFileManager.Location myLocation;

  public JpsFileObject(URI uri, Kind kind, @Nullable JavaFileManager.Location location) {
    super(uri, kind);
    myLocation = location;
  }

  @Nullable
  public JavaFileManager.Location getLocation() {
    return myLocation;
  }

  protected static Kind findKind(String name) {
    for (Kind kind : ourAvailableKinds) {
      if (kind != Kind.OTHER && name.regionMatches(true, name.length() - kind.extension.length(), kind.extension, 0, kind.extension.length())) {
        return kind;
      }
    }
    return Kind.OTHER;
  }

  /**
   * @param path
   * @param caseSensitiveFS
   * @return null means the file manager should delegate to base implementation
   */
  @Nullable
  protected abstract String inferBinaryName(Iterable<? extends File> path, boolean caseSensitiveFS);

  @Override
  public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
    final CharSequence content = getCharContent(ignoreEncodingErrors);
    if (content == null) {
      throw new UnsupportedOperationException();
    }
    // optimizations: avoid chars copying if possible
    if (content instanceof CharArrayCharSequence) {
      final CharArrayCharSequence _content = (CharArrayCharSequence)content;
      return new CharArrayReader(_content.getBackendArray(), _content.getStart(), _content.length());
    }
    if (content instanceof CharBuffer) {
      final CharBuffer buffer = (CharBuffer)content;
      if (buffer.hasArray()) {
        return new CharArrayReader(buffer.array(), buffer.arrayOffset(), buffer.length());
      }
    }
    return new StringReader(content.toString());
  }

  @SuppressWarnings("Duplicates")
  @NotNull
  protected static CharSequence loadCharContent(@NotNull File file, @Nullable String encoding) throws IOException {
    // FileUtil.loadText clones char array if length mismatch
    final FileInputStream stream = new FileInputStream(file);
    try {
      final Reader reader = encoding == null ? new InputStreamReader(stream) : new InputStreamReader(stream, encoding);
      try {
        // channel allows to avoid extra call to get file size because fd is reused, see Files.readAllBytes
        char[] chars = new char[(int)stream.getChannel().size()];
        int count = 0;
        while (count < chars.length) {
          int n = reader.read(chars, count, chars.length - count);
          if (n <= 0) {
            break;
          }
          count += n;
        }
        return new CharArrayCharSequence(chars, 0, count);
      }
      finally {
        reader.close();
      }
    }
    finally {
      stream.close();
    }
  }

  @Override
  public int hashCode() {
    return toUri().hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    // todo: check if this is fast enough to rely on URI.equals() here
    return this == obj || (obj instanceof JpsFileObject && toUri().equals(((JpsFileObject)obj).toUri()));
  }
}
