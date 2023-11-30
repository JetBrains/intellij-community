// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.psi.impl;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonValue;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class JsonFileImpl extends PsiFileBase implements JsonFile {

  public JsonFileImpl(FileViewProvider fileViewProvider, Language language) {
    super(fileViewProvider, language);
  }

  @Override
  public @NotNull FileType getFileType() {
    return getViewProvider().getFileType();
  }

  @Override
  public @Nullable JsonValue getTopLevelValue() {
    return PsiTreeUtil.getChildOfType(this, JsonValue.class);
  }

  @Override
  public @NotNull List<JsonValue> getAllTopLevelValues() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, JsonValue.class);
  }

  @Override
  public String toString() {
    return "JsonFile: " + getName();
  }
}
