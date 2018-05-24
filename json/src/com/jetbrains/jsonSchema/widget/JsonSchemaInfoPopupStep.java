// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.widget;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.jetbrains.jsonSchema.JsonSchemaMappingsConfigurable;
import com.jetbrains.jsonSchema.JsonSchemaMappingsProjectConfiguration;
import com.jetbrains.jsonSchema.UserDefinedJsonSchemaConfiguration;
import com.jetbrains.jsonSchema.extension.JsonSchemaInfo;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.jetbrains.jsonSchema.widget.JsonSchemaStatusPopup.*;

class JsonSchemaInfoPopupStep extends BaseListPopupStep<JsonSchemaInfo> {
  private final Project myProject;
  private final VirtualFile myVirtualFile;
  @NotNull private final JsonSchemaService myService;
  private static final Icon EMPTY_ICON = JBUI.scale(EmptyIcon.create(AllIcons.General.Add.getIconWidth()));

  public JsonSchemaInfoPopupStep(@NotNull List<JsonSchemaInfo> allSchemas, @NotNull Project project, @NotNull VirtualFile virtualFile,
                                 @NotNull JsonSchemaService service) {
    super(null, allSchemas);
    myProject = project;
    myVirtualFile = virtualFile;
    myService = service;
  }

  @NotNull
  @Override
  public String getTextFor(JsonSchemaInfo value) {
    return value.getDescription();
  }

  @Override
  public Icon getIconFor(JsonSchemaInfo value) {
    if (value == ADD_MAPPING) {
      return AllIcons.General.Add;
    }

    if (value == EDIT_MAPPINGS) {
      return AllIcons.Actions.Edit;
    }

    if (value == LOAD_REMOTE) {
      return AllIcons.Actions.Refresh;
    }

    return EMPTY_ICON;
  }

  @Nullable
  @Override
  public ListSeparator getSeparatorAbove(JsonSchemaInfo value) {
    List<JsonSchemaInfo> values = getValues();
    int index = values.indexOf(value);
    if (index - 1 >= 0) {
      JsonSchemaInfo info = values.get(index - 1);
      if (info == EDIT_MAPPINGS || info == ADD_MAPPING) {
        return new ListSeparator("Registered schemas");
      }
      if (value.getProvider() == null && info.getProvider() != null) {
        return new ListSeparator("SchemaStore.org schemas");
      }
    }
    return null;
  }

  @Override
  public PopupStep onChosen(JsonSchemaInfo selectedValue, boolean finalChoice) {
    if (finalChoice) {
      if (selectedValue == EDIT_MAPPINGS || selectedValue == ADD_MAPPING) {
        return doFinalStep(() -> runSchemaEditorForCurrentFile());
      }
      else if (selectedValue == LOAD_REMOTE) {
        return doFinalStep(() -> myService.triggerUpdateRemote());
      }
      else {
        setMapping(selectedValue, myVirtualFile, myProject);
        return doFinalStep(() -> myService.reset());
      }
    }
    return PopupStep.FINAL_CHOICE;
  }

  private void runSchemaEditorForCurrentFile() {
    JsonSchemaMappingsConfigurable configurable = new JsonSchemaMappingsConfigurable(myProject);
    JsonSchemaMappingsProjectConfiguration mappingsConf = JsonSchemaMappingsProjectConfiguration.getInstance(myProject);

    ShowSettingsUtil.getInstance().editConfigurable(myProject, configurable, () -> {
      UserDefinedJsonSchemaConfiguration mappingForFile = mappingsConf.findMappingForFile(myVirtualFile);
      if (mappingForFile == null) {
        UserDefinedJsonSchemaConfiguration configuration = configurable.addProjectSchema();
        String relativePath = VfsUtilCore.getRelativePath(myVirtualFile, myProject.getBaseDir());
        configuration.patterns.add(new UserDefinedJsonSchemaConfiguration.Item(
          relativePath == null ? myVirtualFile.getUrl() : relativePath, false, false));
        mappingForFile = configuration;
      }

      configurable.selectInTree(mappingForFile);
    });
  }

  @Override
  public boolean isSpeedSearchEnabled() {
    return true;
  }

  private static void setMapping(@Nullable JsonSchemaInfo selectedValue, @NotNull VirtualFile virtualFile, @NotNull Project project) {
    JsonSchemaMappingsProjectConfiguration configuration = JsonSchemaMappingsProjectConfiguration.getInstance(project);

    VirtualFile projectBaseDir = project.getBaseDir();

    UserDefinedJsonSchemaConfiguration mappingForFile = configuration.findMappingForFile(virtualFile);
    if (mappingForFile != null) {
      for (UserDefinedJsonSchemaConfiguration.Item pattern : mappingForFile.patterns) {
        if (Objects.equals(VfsUtil.findRelativeFile(projectBaseDir, pattern.path), virtualFile)
              || virtualFile.getUrl().equals(pattern.path)) {
          mappingForFile.patterns.remove(pattern);
          if (mappingForFile.patterns.size() == 0 && mappingForFile.isApplicationDefined()) {
            configuration.removeConfiguration(mappingForFile);
          }
          else {
            mappingForFile.refreshPatterns();
          }
          break;
        }
      }
    }

    if (selectedValue == null) return;

    String path = VfsUtilCore.getRelativePath(virtualFile, projectBaseDir);
    if (path == null) {
      path = virtualFile.getUrl();
    }

    UserDefinedJsonSchemaConfiguration existing = configuration.findMappingBySchemaInfo(selectedValue);
    UserDefinedJsonSchemaConfiguration.Item item = new UserDefinedJsonSchemaConfiguration.Item(path, false, false);
    if (existing != null) {
      if (!existing.patterns.contains(item)) {
        existing.patterns.add(item);
        existing.refreshPatterns();
      }
    }
    else {
      configuration.addConfiguration(new UserDefinedJsonSchemaConfiguration(selectedValue.getDescription(),
                                                                            selectedValue.getSchemaVersion(),
                                                                            selectedValue.getUrl(project),
                                                                            true,
                                                                            Collections.singletonList(item)));
    }
  }
}
