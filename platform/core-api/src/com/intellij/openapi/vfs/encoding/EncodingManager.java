// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.encoding;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.Collection;

public abstract class EncodingManager extends EncodingRegistry {
  @NonNls public static final String PROP_NATIVE2ASCII_SWITCH = "native2ascii";
  @NonNls public static final String PROP_PROPERTIES_FILES_ENCODING = "propertiesFilesEncoding";

  @NotNull
  public static EncodingManager getInstance() {
    return ApplicationManager.getApplication().getService(EncodingManager.class);
  }

  @NotNull
  public abstract Collection<Charset> getFavorites();

  @Override
  public abstract boolean isNative2AsciiForPropertiesFiles();

  public abstract void setNative2AsciiForPropertiesFiles(VirtualFile virtualFile, boolean native2Ascii);

  /**
   * @return returns empty for system default
   */
  @NotNull
  public abstract String getDefaultCharsetName();

  public void setDefaultCharsetName(@NotNull String name) {
    throw new UnsupportedOperationException("Not implemented");
  }

  /**
   * @return null for system-default
   */
  @Override
  @Nullable
  public abstract Charset getDefaultCharsetForPropertiesFiles(@Nullable VirtualFile virtualFile);

  public abstract void setDefaultCharsetForPropertiesFiles(@Nullable VirtualFile virtualFile, @Nullable Charset charset);

  /**
   * @return encoding used by default in {@link com.intellij.execution.configurations.GeneralCommandLine}
   */
  @Override
  public abstract @NotNull Charset getDefaultConsoleEncoding();

  @Nullable
  public abstract Charset getCachedCharsetFromContent(@NotNull Document document);

  public boolean shouldAddBOMForNewUtf8File() {
    return false;
  }
}
