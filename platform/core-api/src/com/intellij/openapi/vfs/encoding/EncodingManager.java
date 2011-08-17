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
package com.intellij.openapi.vfs.encoding;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.PropertyChangeListener;
import java.nio.charset.Charset;
import java.util.Collection;

/**
 * @author cdr
 */
public abstract class EncodingManager {
  @NonNls public static final String PROP_NATIVE2ASCII_SWITCH = "native2ascii";
  @NonNls public static final String PROP_PROPERTIES_FILES_ENCODING = "propertiesFilesEncoding";

  public static EncodingManager getInstance() {
    return ServiceManager.getService(EncodingManager.class);
  }

  @NotNull
  public abstract Collection<Charset> getFavorites();

  /**
   * @param virtualFile  file to get encoding for
   * @param useParentDefaults true to determine encoding from the parent
   * @return encoding configured for this file in Settings|File Encodings or,
   *         if useParentDefaults is true, encoding configured for nearest parent of virtualFile or,
   *         null if there is no configured encoding found.
   */
  public abstract Charset getEncoding(@Nullable VirtualFile virtualFile, boolean useParentDefaults);

  public abstract void setEncoding(@Nullable VirtualFile virtualFileOrDir, @Nullable Charset charset);

  public abstract boolean isUseUTFGuessing(VirtualFile virtualFile);

  public abstract void setUseUTFGuessing(VirtualFile virtualFile, boolean useUTFGuessing);

  public abstract boolean isNative2AsciiForPropertiesFiles(VirtualFile virtualFile);

  public abstract void setNative2AsciiForPropertiesFiles(VirtualFile virtualFile, boolean native2Ascii);

  /**
   * @return name of default charset configured in Settings|File Encodings|IDE encoding
   */
  public abstract Charset getDefaultCharset();

  public String getDefaultCharsetName() {
    return getDefaultCharset().displayName();
  }

  public void setDefaultCharsetName(String name) {
    throw new UnsupportedOperationException("Not implemented");
  }

  /**
   * @return null for system-default
   */
  public abstract Charset getDefaultCharsetForPropertiesFiles(@Nullable VirtualFile virtualFile);
  public abstract void setDefaultCharsetForPropertiesFiles(@Nullable VirtualFile virtualFile, @Nullable Charset charset);

  /**
   * @deprecated use {@link EncodingManager#addPropertyChangeListener(java.beans.PropertyChangeListener, com.intellij.openapi.Disposable)} instead
   */
  public abstract void addPropertyChangeListener(@NotNull PropertyChangeListener listener);
  public abstract void addPropertyChangeListener(@NotNull PropertyChangeListener listener, @NotNull Disposable parentDisposable);

  public abstract void removePropertyChangeListener(@NotNull PropertyChangeListener listener);

  public abstract Charset getCachedCharsetFromContent(@NotNull Document document);
}
