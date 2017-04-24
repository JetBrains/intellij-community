/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.jsonSchema.impl;

import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonObject;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Irina.Chernushina on 8/27/2015.
 */
public class JsonSchemaReader {
  private static final int MAX_SCHEMA_LENGTH = FileUtilRt.MEGABYTE;
  public static final Logger LOG = Logger.getInstance("#com.jetbrains.jsonSchema.impl.JsonSchemaReader");
  public static final NotificationGroup ERRORS_NOTIFICATION = NotificationGroup.logOnlyGroup("JSON Schema");

  @NotNull private final JsonObject myRoot;

  public JsonSchemaReader(@NotNull JsonObject root) {
    myRoot = root;
  }

  @NotNull
  public static JsonSchemaReader create(@NotNull Project project, @NotNull VirtualFile key) throws Exception {
    final PsiFile psiFile = PsiManager.getInstance(project).findFile(key);
    if (!(psiFile instanceof JsonFile)) throw new Exception(String.format("Can not load PSI for JSON Schema file '%s'", key.getName()));
    final JsonObject value = ObjectUtils.tryCast(((JsonFile)psiFile).getTopLevelValue(), JsonObject.class);
    if (value == null)
      throw new Exception(String.format("JSON Schema file '%s' must contain only one top-level object", key.getName()));
    return new JsonSchemaReader(value);
  }

  // todo code beauty
  public JsonSchemaObject read() {
    return new ReadJsonSchemaFromPsi().read(myRoot);
  }

  @Nullable
  public static String checkIfValidJsonSchema(@NotNull final Project project, @NotNull final VirtualFile file) {
    final long length = file.getLength();
    final String fileName = file.getName();
    if (length > MAX_SCHEMA_LENGTH) {
      return String.format("JSON schema was not loaded from '%s' because it's too large (file size is %d bytes).", fileName, length);
    }
    if (length == 0) {
      return String.format("JSON schema was not loaded from '%s'. File is empty.", fileName);
    }
    try {
      create(project, file).read();
    } catch (Exception e) {
      final String message = String.format("JSON Schema not found or contain error in '%s': %s", fileName, e.getMessage());
      LOG.info(message);
      return message;
    }
    return null;
  }

  public static class SchemaUrlSplitter {
    @Nullable
    private final String mySchemaId;
    @NotNull
    private final String myRelativePath;

    public SchemaUrlSplitter(@NotNull final String ref) {
      if (!ref.startsWith("#/")) {
        int idx = ref.indexOf("#/");
        if (idx == -1) {
          mySchemaId = ref.endsWith("#") ? ref.substring(0, ref.length() - 1) : ref;
          myRelativePath = "";
        } else {
          mySchemaId = ref.substring(0, idx);
          myRelativePath = ref.substring(idx);
        }
      } else {
        mySchemaId = null;
        myRelativePath = ref;
      }
    }

    public boolean isAbsolute() {
      return mySchemaId != null;
    }

    @Nullable
    public String getSchemaId() {
      return mySchemaId;
    }

    @NotNull
    public String getRelativePath() {
      return myRelativePath;
    }
  }
}