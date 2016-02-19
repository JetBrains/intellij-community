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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import com.intellij.ui.LightColors;
import com.jetbrains.jsonSchema.JsonSchemaMappingsConfigurable;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

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
    final List<Pair<Boolean, String>> descriptors = myJsonSchemaService.getMatchingSchemaDescriptors(file);
    if (descriptors == null || descriptors.size() <= 1) return null;

    final String message = createMessage(descriptors);
    final EditorNotificationPanel panel = new EditorNotificationPanel() {
      @Override
      public Color getBackground() {
        return LightColors.RED;
      }
    };
    panel.setText(message);
    panel.createActionLabel("Edit JSON Schema Mappings", new Runnable() {
      @Override
      public void run() {
        ShowSettingsUtil.getInstance().editConfigurable(myProject, new JsonSchemaMappingsConfigurable(myProject));
        EditorNotifications.getInstance(myProject).updateNotifications(file);
      }
    });
    return panel;
  }

  private static String createMessage(@NotNull final List<Pair<Boolean, String>> descriptors) {
    boolean haveSystemSchemas = false;
    for (Pair<Boolean, String> pair : descriptors) {
      haveSystemSchemas |= !Boolean.TRUE.equals(pair.getFirst());
    }
    boolean withTypes = haveSystemSchemas;
    final List<String> names = new ArrayList<>();
    for (Pair<Boolean, String> pair : descriptors) {
      if (withTypes) {
        names.add((Boolean.TRUE.equals(pair.getFirst()) ? "user" : "system") + " schema '" + pair.getSecond() + "'");
      } else {
        names.add(pair.getSecond());
      }
    }
    return "<html>There are several JSON Schemas mapped to this file: " + StringUtil.join(names, "; ") + "</html>";
  }
}
