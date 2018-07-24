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

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import com.intellij.ui.LightColors;
import com.jetbrains.jsonSchema.extension.SchemaType;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.settings.mappings.JsonSchemaMappingsConfigurable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Irina.Chernushina on 2/19/2016.
 */
public class JsonSchemaConflictNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> {
  private static final Key<EditorNotificationPanel> KEY = Key.create("json.schema.conflict.notification.panel");

  @NotNull
  private final Project myProject;
  @NotNull
  private final JsonSchemaService myJsonSchemaService;

  public JsonSchemaConflictNotificationProvider(@NotNull Project project,
                                                @NotNull JsonSchemaService jsonSchemaService) {
    myProject = project;
    myJsonSchemaService = jsonSchemaService;
  }

  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file, @NotNull FileEditor fileEditor) {
    if (!myJsonSchemaService.isApplicableToFile(file)) return null;
    final Collection<VirtualFile> schemaFiles = myJsonSchemaService.getSchemaFilesForFile(file);
    if (schemaFiles.size() <= 1) return null;

    final String message = createMessage(schemaFiles, myJsonSchemaService,
                                         "; ", "<html>There are several JSON Schemas mapped to this file: ", "</html>");
    if (message == null) return null;

    final EditorNotificationPanel panel = new EditorNotificationPanel(LightColors.RED);
    panel.setText(message);
    panel.createActionLabel("Edit JSON Schema Mappings", () -> {
      ShowSettingsUtil.getInstance().editConfigurable(myProject, new JsonSchemaMappingsConfigurable(myProject));
      EditorNotifications.getInstance(myProject).updateNotifications(file);
    });
    return panel;
  }

  public static String createMessage(@NotNull final Collection<VirtualFile> schemaFiles,
                                     @NotNull JsonSchemaService jsonSchemaService,
                                     @NotNull String separator,
                                     @NotNull String prefix,
                                     @NotNull String suffix) {
    final List<Pair<Boolean, String>> pairList = schemaFiles.stream()
      .map(file -> jsonSchemaService.getSchemaProvider(file))
      .filter(Objects::nonNull)
      .map(provider -> Pair.create(SchemaType.userSchema.equals(provider.getSchemaType()), provider.getName()))
      .collect(Collectors.toList());

    final long numOfSystemSchemas = pairList.stream().filter(pair -> !pair.getFirst()).count();
    // do not report anything if there is only one system schema and one user schema (user overrides schema that we provide)
    if (pairList.size() == 2 && numOfSystemSchemas == 1) return null;

    final boolean withTypes = numOfSystemSchemas > 0;
    return pairList.stream().map(pair -> {
      if (withTypes) {
        return String.format("%s schema '%s'", Boolean.TRUE.equals(pair.getFirst()) ? "user" : "system", pair.getSecond());
      }
      else {
        return pair.getSecond();
      }
    }).collect(Collectors.joining(separator, prefix, suffix));
  }
}
