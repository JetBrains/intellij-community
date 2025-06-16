// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.javac;

import org.jetbrains.jps.builders.java.JavaSourceTransformer;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.tools.*;
import java.io.*;
import java.net.URI;
import java.util.Collection;

final class TransformableJavaFileObject implements JavaFileObject {
  private final JavaFileObject myOriginal;
  private final Collection<? extends JavaSourceTransformer> myTransformers;

  TransformableJavaFileObject(JavaFileObject original, Collection<? extends JavaSourceTransformer> transformers) {
    myOriginal = original;
    myTransformers = transformers;
  }

  public JavaFileObject getOriginal() {
    return myOriginal;
  }

  @Override
  public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
    // todo: cache transformed content?
    final File file = new File(myOriginal.toUri());
    CharSequence content = myOriginal.getCharContent(ignoreEncodingErrors);
    for (JavaSourceTransformer transformer : myTransformers) {
      content = transformer.transform(file, content);
    }
    return content;
  }

  @Override
  public InputStream openInputStream() throws IOException {
    // todo: more accurately would be returning a stream for transformed content
    return myOriginal.openInputStream();
  }

  @Override
  public Kind getKind() {
    return myOriginal.getKind();
  }

  @Override
  public boolean isNameCompatible(String simpleName, Kind kind) {
    return myOriginal.isNameCompatible(simpleName, kind);
  }

  @Override
  public NestingKind getNestingKind() {
    return myOriginal.getNestingKind();
  }

  @Override
  public Modifier getAccessLevel() {
    return myOriginal.getAccessLevel();
  }

  @Override
  public URI toUri() {
    return myOriginal.toUri();
  }

  @Override
  public String getName() {
    return myOriginal.getName();
  }

  @Override
  public OutputStream openOutputStream() throws IOException {
    return myOriginal.openOutputStream();
  }

  @Override
  public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
    return myOriginal.openReader(ignoreEncodingErrors);
  }

  @Override
  public Writer openWriter() throws IOException {
    return myOriginal.openWriter();
  }

  @Override
  public long getLastModified() {
    return myOriginal.getLastModified();
  }

  @Override
  public boolean delete() {
    return myOriginal.delete();
  }

  @Override
  public String toString() {
    // must implement like this because toString() is called inside com.sun.tools.javac.jvm.ClassWriter instead of getName()
    return getName();
  }
}
