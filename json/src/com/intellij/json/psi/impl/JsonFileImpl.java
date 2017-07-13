package com.intellij.json.psi.impl;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.json.JsonLanguage;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonValue;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class JsonFileImpl extends PsiFileBase implements JsonFile {

  public JsonFileImpl(FileViewProvider fileViewProvider) {
    super(fileViewProvider, JsonLanguage.INSTANCE);
  }

  @NotNull
  @Override
  public FileType getFileType() {
    return getViewProvider().getFileType();
  }

  @Nullable
  @Override
  public JsonValue getTopLevelValue() {
    return PsiTreeUtil.getChildOfType(this, JsonValue.class);
  }

  @NotNull
  @Override
  public List<JsonValue> getAllTopLevelValues() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, JsonValue.class);
  }

  @Override
  public String toString() {
    return "JsonFile: " + getName();
  }
}
