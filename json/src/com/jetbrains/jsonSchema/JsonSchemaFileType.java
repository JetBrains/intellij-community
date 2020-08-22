// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema;

import com.intellij.icons.AllIcons;
import com.intellij.json.JsonBundle;
import com.intellij.json.JsonLanguage;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.ex.FileTypeIdentifiableByVirtualFile;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * To make plugin github.com/BlueBoxWare/LibGDXPlugin happy
 * @author Irina.Chernushina on 4/1/2016.
 * @deprecated This file type is no longer registered
 */
@Deprecated
public final class JsonSchemaFileType extends LanguageFileType implements FileTypeIdentifiableByVirtualFile {
  public static final JsonSchemaFileType INSTANCE = new JsonSchemaFileType();

  private JsonSchemaFileType() {
    super(JsonLanguage.INSTANCE);
  }

  @NotNull
  @Override
  public String getName() {
    return "JSON Schema";
  }

  @NotNull
  @Override
  public String getDescription() {
    return JsonBundle.message("json.schema.desc");
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return "json";
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return AllIcons.FileTypes.JsonSchema;
  }

  @Override
  public boolean isMyFileType(@NotNull VirtualFile file) {
    return false;
  }
}
