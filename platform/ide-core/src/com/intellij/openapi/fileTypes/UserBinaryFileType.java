// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.options.SettingsEditor;

public class UserBinaryFileType extends UserFileType<UserBinaryFileType> {
  public static final UserBinaryFileType INSTANCE = new UserBinaryFileType();
  protected UserBinaryFileType() {
  }

  @Override
  public SettingsEditor<UserBinaryFileType> getEditor() {
    return null;
  }

  @Override
  public boolean isBinary() {
    return true;
  }
}
