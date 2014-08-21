package com.intellij.json.psi.impl;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.json.JsonFileType;
import com.intellij.json.JsonLanguage;
import com.intellij.json.psi.JsonArray;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonValue;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JsonFileImpl extends PsiFileBase implements JsonFile {

  public JsonFileImpl(FileViewProvider fileViewProvider) {
    super(fileViewProvider, JsonLanguage.INSTANCE);
  }

  @NotNull
  @Override
  public FileType getFileType() {
    return JsonFileType.INSTANCE;
  }

  @Nullable
  @Override
  public JsonValue getTopLevelValue() {
    final PsiElement[] children = getChildren();
    for (PsiElement child : children) {
      if (child instanceof JsonObject || child instanceof JsonArray) {
        return (JsonValue) child;
      }
    }
    return null;
  }
}
