// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class UserFileType<T extends UserFileType<T>> implements FileType, Cloneable {
  @NotNull private String myName = "";
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
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  @NotNull
  public String getDescription() {
    return myDescription;
  }

  public void setName(@NotNull String name) {
    myName = name;
  }

  public void setDescription(@NlsContexts.Label String description) {
    myDescription = description;
  }

  @Override
  @NotNull
  public String getDefaultExtension() {
    return "";
  }

  @Override
  public Icon getIcon() {
    Icon icon = myIcon;
    if (icon == null) {
      if (myIconPath != null) {
        icon = IconLoader.getIcon(myIconPath);
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
