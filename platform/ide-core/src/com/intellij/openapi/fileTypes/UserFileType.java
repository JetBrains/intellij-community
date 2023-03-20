// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class UserFileType<T extends UserFileType<T>> implements FileType, Cloneable {
  private @NotNull String myName = "";
  private @NlsContexts.Label String myDescription = "";

  private Icon myIcon;
  private String myIconPath;

  protected UserFileType() {
  }

  public abstract SettingsEditor<T> getEditor();

  @Override
  public UserFileType<T> clone() {
    try {
      //noinspection unchecked
      return (UserFileType<T>)super.clone();
    }
    catch (CloneNotSupportedException e) {
      return null; //Can't be
    }
  }

  @Override
  public @NotNull String getName() {
    return myName;
  }

  @Override
  public @NotNull String getDescription() {
    return myDescription;
  }

  public void setName(@NotNull String name) {
    myName = name;
  }

  public void setDescription(@NlsContexts.Label String description) {
    myDescription = description;
  }

  @Override
  public @NotNull String getDefaultExtension() {
    ExtensionFileNameMatcher ext = ContainerUtil.findInstance(FileTypeManager.getInstance().getAssociations(this), ExtensionFileNameMatcher.class);
    return ext == null ? "" : ext.getExtension();
  }

  @Override
  public Icon getIcon() {
    Icon icon = myIcon;
    if (icon == null) {
      if (myIconPath != null) {
        icon = IconLoader.getIcon(myIconPath, UserFileType.class.getClassLoader());
        myIcon = icon;
      }

      if (icon == null) {
        // to not load PlatformIcons on UserFileType instantiation
        icon = PlatformIcons.CUSTOM_FILE_ICON;
      }
    }
    return icon;
  }

  public void copyFrom(@NotNull UserFileType<T> newType) {
    myName = newType.getName();
    myDescription = newType.getDescription();
  }

  public void setIcon(@NotNull Icon icon) {
    myIcon = icon;
  }

  public void setIconPath(@NotNull String value) {
    myIconPath = value;
  }

  @Override
  public String toString() {
    return getName();
  }
}
