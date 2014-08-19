package com.intellij.json.psi;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.json.JsonFileType;
import com.intellij.json.JsonLanguage;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;

public class JsonFile extends PsiFileBase {

  public JsonFile(FileViewProvider fileViewProvider) {
    super(fileViewProvider, JsonLanguage.INSTANCE);
  }

  @NotNull
  @Override
  public FileType getFileType() {
    return JsonFileType.INSTANCE;
  }
}
