/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.vfs.VirtualFile;

import javax.tools.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

/**
 * @author cdr
 */
@SuppressWarnings({"Since15"})
public abstract class FileVirtualObject extends SimpleJavaFileObject {
  public FileVirtualObject(URI uri, Kind kind) {
    super(uri, kind);
  }

  protected abstract VirtualFile getVirtualFile();
  @Override
  public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
    VirtualFile virtualFile = getVirtualFile();
    if (virtualFile == null) return null;
    return LoadTextUtil.loadText(virtualFile);
  }

  @Override
  public InputStream openInputStream() throws IOException {
    return getVirtualFile().getInputStream();
  }

  @Override
  public OutputStream openOutputStream() throws IOException {
    return getVirtualFile().getOutputStream(this);
  }

  @Override
  public String toString() {
    return toUri().toString();
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
