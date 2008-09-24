package com.intellij.openapi.vfs.encoding;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.editor.Document;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.PropertyChangeListener;
import java.nio.charset.Charset;
import java.util.Collection;

/**
 * @author cdr
 */
public abstract class EncodingManager implements ApplicationComponent {
  @NonNls public static final String PROP_NATIVE2ASCII_SWITCH = "native2ascii";
  @NonNls public static final String PROP_PROPERTIES_FILES_ENCODING = "propertiesFilesEncoding";

  public static EncodingManager getInstance() {
    return ServiceManager.getService(EncodingManager.class);
  }

  @NotNull
  public abstract Collection<Charset> getFavorites();

  public abstract Charset getEncoding(@Nullable VirtualFile virtualFile, boolean useParentDefaults);

  public abstract void setEncoding(@Nullable VirtualFile virtualFileOrDir, @Nullable Charset charset);

  public abstract boolean isUseUTFGuessing(VirtualFile virtualFile);

  public abstract void setUseUTFGuessing(VirtualFile virtualFile, boolean useUTFGuessing);

  public abstract boolean isNative2AsciiForPropertiesFiles(VirtualFile virtualFile);

  public abstract void setNative2AsciiForPropertiesFiles(VirtualFile virtualFile, boolean native2Ascii);

  // returns name of default charset configured in File|Template project settings|File encoding|Project
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

  public abstract void addPropertyChangeListener(PropertyChangeListener listener);

  public abstract void removePropertyChangeListener(PropertyChangeListener listener);

  public abstract Charset getCachedCharsetFromContent(@NotNull Document document);
}