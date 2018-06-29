// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import com.intellij.json.JsonLanguage;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.jsonSchema.extension.JsonSchemaEnabler;

public class JsonSchemaInJsonFilesEnabler implements JsonSchemaEnabler {
  @Override
  public boolean isEnabledForFile(VirtualFile file) {
    FileType type = file.getFileType();
    return type instanceof LanguageFileType && ((LanguageFileType)type).getLanguage() instanceof JsonLanguage;
  }
}
