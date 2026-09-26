// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.core;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;

@ApiStatus.Internal
public class CoreEncodingProjectManager extends EncodingProjectManager {
  @Override
  public boolean isNative2Ascii(@NotNull VirtualFile virtualFile) {
    return false;
  }

  @Override
  public @NotNull Charset getDefaultCharset() {
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

  @Override
  public @NotNull Collection<Charset> getFavorites() {
    return Collections.singletonList(StandardCharsets.UTF_8);
  }

  @Override
  public void setNative2AsciiForPropertiesFiles(VirtualFile virtualFile, boolean native2Ascii) {

  }

  @Override
  public @NotNull String getDefaultCharsetName() {
    return getDefaultCharset().name();
  }

  @Override
  public @Nullable Charset getDefaultCharsetForPropertiesFiles(@Nullable VirtualFile virtualFile) {
    return null;
  }

  @Override
  public void setDefaultCharsetForPropertiesFiles(@Nullable VirtualFile virtualFile, @Nullable Charset charset) {

  }

  @Override
  public @NotNull Charset getDefaultConsoleEncoding() {
    return CharsetToolkit.getDefaultSystemCharset();
  }

  @Override
  public @Nullable Charset getCachedCharsetFromContent(@NotNull Document document) {
    return null;
  }

  @Override
  public void setDefaultCharsetName(@NotNull String name) {

  }
}
