// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.encoding;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.charset.Charset;
import java.util.Collection;

public abstract class EncodingManager extends EncodingRegistry {
  public static final @NonNls String PROP_NATIVE2ASCII_SWITCH = "native2ascii";
  public static final @NonNls String PROP_PROPERTIES_FILES_ENCODING = "propertiesFilesEncoding";

  /**
   * Property name of events fired when the default encoding is changed.
   */
  public static final @NonNls String PROP_DEFAULT_FILES_ENCODING = "defaultFilesEncoding";

  public static @NotNull EncodingManager getInstance() {
    return ApplicationManager.getApplication().getService(EncodingManager.class);
  }

  public abstract @NotNull @Unmodifiable Collection<Charset> getFavorites();

  @Override
  public abstract boolean isNative2AsciiForPropertiesFiles();

  public abstract void setNative2AsciiForPropertiesFiles(VirtualFile virtualFile, boolean native2Ascii);

  /**
   * @return returns empty for system default
   */
  public abstract @NotNull String getDefaultCharsetName();

  public void setDefaultCharsetName(@NotNull String name) {
    throw new UnsupportedOperationException("Not implemented");
  }

  /**
   * @return null for system-default
   */
  @Override
  public abstract @Nullable Charset getDefaultCharsetForPropertiesFiles(@Nullable VirtualFile virtualFile);

  public abstract void setDefaultCharsetForPropertiesFiles(@Nullable VirtualFile virtualFile, @Nullable Charset charset);

  /**
   * @return encoding used by default in {@link com.intellij.execution.configurations.GeneralCommandLine}
   */
  @Override
  public abstract @NotNull Charset getDefaultConsoleEncoding();

  public abstract @Nullable Charset getCachedCharsetFromContent(@NotNull Document document);

  public boolean shouldAddBOMForNewUtf8File() {
    return false;
  }
}
