// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema;

import com.intellij.icons.AllIcons;
import com.intellij.ide.FileIconProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.jsonSchema.extension.JsonSchemaEnabler;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Irina.Chernushina on 5/23/2017.
 */
public class JsonSchemaIconProvider implements FileIconProvider {
  @Nullable
  @Override
  public Icon getIcon(@NotNull VirtualFile file, int flags, @Nullable Project project) {
    if (project != null
        && JsonSchemaEnabler.EXTENSION_POINT_NAME.getExtensionList().stream().anyMatch(e -> e.canBeSchemaFile(file))) {
      JsonSchemaService service = JsonSchemaService.Impl.get(project);
      if (service.isApplicableToFile(file) && service.isSchemaFile(file)) {
        return AllIcons.FileTypes.JsonSchema;
      }
    }
    return null;
  }
}
