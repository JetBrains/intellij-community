/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import org.jetbrains.jps.PathUtils;
import org.jetbrains.jps.builders.java.JavaSourceTransformer;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.tools.JavaFileObject;
import java.io.*;
import java.net.URI;
import java.util.Collection;

/**
 * @author Eugene Zhuravlev
 */
public class TransformableJavaFileObject implements JavaFileObject {
  private final JavaFileObject myOriginal;
  private final Collection<JavaSourceTransformer> myTransformers;

  public TransformableJavaFileObject(JavaFileObject original, Collection<JavaSourceTransformer> transformers) {
    myOriginal = original;
    myTransformers = transformers;
  }

  public JavaFileObject getOriginal() {
    return myOriginal;
  }

  @Override
  public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
    // todo: cache transformed content?
    final File file = PathUtils.convertToFile(myOriginal.toUri());
    CharSequence content = myOriginal.getCharContent(ignoreEncodingErrors);
    for (JavaSourceTransformer transformer : myTransformers) {
      content = transformer.transform(file, content);
    }
    return content;
  }

  @Override
  public InputStream openInputStream() throws IOException {
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
  public final String toString() {
    // must implement like this because toString() is called inside com.sun.tools.javac.jvm.ClassWriter instead of getName()
    return getName();  
  }
}
