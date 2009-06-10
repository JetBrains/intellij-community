package com.intellij.compiler.impl.javaCompiler.api;

import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.vfs.VirtualFile;

import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

/**
 * @author cdr
 */
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
