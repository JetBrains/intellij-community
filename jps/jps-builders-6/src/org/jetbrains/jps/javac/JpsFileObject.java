// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.javac;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.CharArrayCharSequence;

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

  public JpsFileObject(URI uri, Kind kind) {
    super(uri, kind);
  }

  protected static Kind findKind(String name) {
    for (Kind kind : ourAvailableKinds) {
      if (kind != Kind.OTHER && name.endsWith(kind.extension)) {
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
        return new CharArrayReader(buffer.array());
      }
    }
    return new StringReader(content.toString());
  }
}
