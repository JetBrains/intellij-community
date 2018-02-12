// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.core;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.PropertyChangeListener;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;

public class CoreEncodingProjectManager extends EncodingProjectManager {
  @Override
  public boolean isNative2Ascii(@NotNull VirtualFile virtualFile) {
    return false;
  }

  @NotNull
  @Override
  public Charset getDefaultCharset() {
    return CharsetToolkit.getDefaultSystemCharset();
  }

  @Override
  public Charset getEncoding(@Nullable VirtualFile virtualFile, boolean useParentDefaults) {
    return getDefaultCharset();
  }

  @Override
  public void setEncoding(@Nullable VirtualFile virtualFileOrDir, @Nullable Charset charset) {
  }

  @Override
  public boolean isNative2AsciiForPropertiesFiles() {
    return false;
  }

  @NotNull
  @Override
  public Collection<Charset> getFavorites() {
    return Collections.singletonList(CharsetToolkit.UTF8_CHARSET);
  }

  @Override
  public void setNative2AsciiForPropertiesFiles(VirtualFile virtualFile, boolean native2Ascii) {

  }

  @NotNull
  @Override
  public String getDefaultCharsetName() {
    return getDefaultCharset().name();
  }

  @Nullable
  @Override
  public Charset getDefaultCharsetForPropertiesFiles(@Nullable VirtualFile virtualFile) {
    return null;
  }

  @Override
  public void setDefaultCharsetForPropertiesFiles(@Nullable VirtualFile virtualFile, @Nullable Charset charset) {

  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener, @NotNull Disposable parentDisposable) {

  }

  @Nullable
  @Override
  public Charset getCachedCharsetFromContent(@NotNull Document document) {
    return null;
  }

  @Override
  public void setDefaultCharsetName(@NotNull String name) {

  }
}
