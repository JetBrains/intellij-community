// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema;

import com.intellij.icons.AllIcons;
import com.intellij.ide.FileIconProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.jsonSchema.extension.JsonSchemaEnabler;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.light.nodes.JsonSchemaObjectStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class JsonSchemaIconProvider implements FileIconProvider {
  @Override
  public @Nullable Icon getIcon(@NotNull VirtualFile file, int flags, @Nullable Project project) {
    if (project != null
        && JsonSchemaEnabler.EXTENSION_POINT_NAME.getExtensionList().stream().anyMatch(e -> e.canBeSchemaFile(file))) {
      JsonSchemaService service = JsonSchemaService.Impl.get(project);
      if (service.isApplicableToFile(file) && JsonSchemaObjectStorage.getInstance(project).getComputedSchemaRootOrNull(file) != null) {
        return AllIcons.FileTypes.JsonSchema;
      }
    }
    return null;
  }
}
