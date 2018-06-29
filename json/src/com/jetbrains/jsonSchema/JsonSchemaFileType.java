/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.jsonSchema;

import com.intellij.icons.AllIcons;
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
 */
public class JsonSchemaFileType extends LanguageFileType implements FileTypeIdentifiableByVirtualFile {
  public static final JsonSchemaFileType INSTANCE = new JsonSchemaFileType();

  public JsonSchemaFileType() {
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
    return "JSON Schema";
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
