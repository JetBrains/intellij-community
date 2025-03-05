// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.widget;

import com.intellij.icons.AllIcons;
import com.intellij.json.JsonBundle;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopupStepEx;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.NlsContexts.PopupTitle;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.StatusText;
import com.jetbrains.jsonSchema.JsonSchemaMappingsProjectConfiguration;
import com.jetbrains.jsonSchema.UserDefinedJsonSchemaConfiguration;
import com.jetbrains.jsonSchema.extension.JsonSchemaInfo;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.settings.mappings.JsonSchemaMappingsConfigurable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.intellij.openapi.util.NlsContexts.Tooltip;
import static com.jetbrains.jsonSchema.widget.JsonSchemaStatusPopup.*;

public class JsonSchemaInfoPopupStep extends BaseListPopupStep<JsonSchemaInfo> implements ListPopupStepEx<JsonSchemaInfo> {
  private final Project myProject;
  private final @Nullable VirtualFile myVirtualFile;
  private final @NotNull JsonSchemaService myService;
  private static final Icon EMPTY_ICON = JBUIScale.scaleIcon(EmptyIcon.create(AllIcons.General.Add.getIconWidth()));

  public JsonSchemaInfoPopupStep(@NotNull List<JsonSchemaInfo> allSchemas, @NotNull Project project, @Nullable VirtualFile virtualFile,
                                 @NotNull JsonSchemaService service, @Nullable @PopupTitle String title) {
    super(title, allSchemas);
    myProject = project;
    myVirtualFile = virtualFile;
    myService = service;
  }

  @Override
  public @NotNull String getTextFor(JsonSchemaInfo value) {
    return value == null ? "" : value.getDescription();
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

    if (value == IGNORE_FILE) {
      return AllIcons.Vcs.Ignore_file;
    }

    if (value == STOP_IGNORE_FILE) {
      return AllIcons.Actions.AddFile;
    }

    return AllIcons.FileTypes.JsonSchema;
  }

  @Override
  public @Nullable ListSeparator getSeparatorAbove(JsonSchemaInfo value) {
    List<JsonSchemaInfo> values = getValues();
    int index = values.indexOf(value);
    if (index - 1 >= 0) {
      JsonSchemaInfo info = values.get(index - 1);
      if (info == EDIT_MAPPINGS || info == ADD_MAPPING) {
        return new ListSeparator(JsonBundle.message("schema.widget.registered.schemas"));
      }
      if (value.getProvider() == null && info.getProvider() != null) {
        return new ListSeparator(JsonBundle.message("schema.widget.store.schemas"));
      }
    }
    return null;
  }

  @Override
  public PopupStep<?> onChosen(JsonSchemaInfo selectedValue, boolean finalChoice) {
    if (finalChoice) {
      if (selectedValue == EDIT_MAPPINGS || selectedValue == ADD_MAPPING) {
        return doFinalStep(() -> runSchemaEditorForCurrentFile());
      }
      else if (selectedValue == LOAD_REMOTE) {
        return doFinalStep(() -> myService.triggerUpdateRemote());
      }
      else if (selectedValue == IGNORE_FILE) {
        markIgnored(myVirtualFile, myProject);
        return doFinalStep(() -> myService.reset());
      }
      else if (selectedValue == STOP_IGNORE_FILE) {
        unmarkIgnored(myVirtualFile, myProject);
        return doFinalStep(() -> myService.reset());
      }
      else {
        setMapping(selectedValue, myVirtualFile, myProject);
        return doFinalStep(() -> myService.reset());
      }
    }
    return PopupStep.FINAL_CHOICE;
  }

  protected void runSchemaEditorForCurrentFile() {
    assert myVirtualFile != null: "override this method to do without a virtual file!";
    ShowSettingsUtil.getInstance().showSettingsDialog(myProject, JsonSchemaMappingsConfigurable.class, (configurable) -> {
      // For some reason, JsonSchemaMappingsConfigurable.reset is called right after this callback, leading to resetting the customization.
      // Workaround: move this logic inside JsonSchemaMappingsConfigurable.reset.
      configurable.setInitializer(() -> {
        JsonSchemaMappingsProjectConfiguration mappings = JsonSchemaMappingsProjectConfiguration.getInstance(myProject);
        UserDefinedJsonSchemaConfiguration configuration = mappings.findMappingForFile(myVirtualFile);
        if (configuration == null) {
          configuration = configurable.addProjectSchema();
          String relativePath = VfsUtilCore.getRelativePath(myVirtualFile, myProject.getBaseDir());
          configuration.patterns.add(new UserDefinedJsonSchemaConfiguration.Item(
            relativePath == null ? myVirtualFile.getUrl() : relativePath, false, false));
        }
        configurable.selectInTree(configuration);
      });
    });
  }

  @Override
  public boolean isSpeedSearchEnabled() {
    return true;
  }

  @Override
  public @Nullable @Tooltip String getTooltipTextFor(JsonSchemaInfo value) {
    return getDoc(value);
  }

  private static @Nullable @Tooltip String getDoc(JsonSchemaInfo schema) {
    if (schema == null) return null;
    if (schema.getName() == null) return schema.getDocumentation();
    if (schema.getDocumentation() == null) return schema.getName();
    return new HtmlBuilder()
      .append(HtmlChunk.tag("b").addText(schema.getName()))
      .append(HtmlChunk.br())
      .appendRaw(schema.getDocumentation()).toString();
  }

  @Override
  public void setEmptyText(@NotNull StatusText emptyText) {
  }

  private static void markIgnored(@Nullable VirtualFile virtualFile, @NotNull Project project) {
    JsonSchemaMappingsProjectConfiguration configuration = JsonSchemaMappingsProjectConfiguration.getInstance(project);
    configuration.markAsIgnored(virtualFile);
  }

  private static void unmarkIgnored(@Nullable VirtualFile virtualFile, @NotNull Project project) {
    JsonSchemaMappingsProjectConfiguration configuration = JsonSchemaMappingsProjectConfiguration.getInstance(project);
    if (!configuration.isIgnoredFile(virtualFile)) return;
    configuration.unmarkAsIgnored(virtualFile);
  }
  protected void setMapping(@Nullable JsonSchemaInfo selectedValue, @Nullable VirtualFile virtualFile, @NotNull Project project) {
    assert virtualFile != null: "override this method to do without a virtual file!";
    JsonSchemaMappingsProjectConfiguration configuration = JsonSchemaMappingsProjectConfiguration.getInstance(project);

    VirtualFile projectBaseDir = project.getBaseDir();

    UserDefinedJsonSchemaConfiguration mappingForFile = configuration.findMappingForFile(virtualFile);
    if (mappingForFile != null) {
      for (UserDefinedJsonSchemaConfiguration.Item pattern : mappingForFile.patterns) {
        if (Objects.equals(VfsUtil.findRelativeFile(projectBaseDir, pattern.getPathParts()), virtualFile)
              || virtualFile.getUrl().equals(UserDefinedJsonSchemaConfiguration.Item.neutralizePath(pattern.getPath()))) {
          mappingForFile.patterns.remove(pattern);
          if (mappingForFile.patterns.isEmpty() && mappingForFile.isApplicationDefined()) {
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

    String path = projectBaseDir == null ? null : VfsUtilCore.getRelativePath(virtualFile, projectBaseDir);
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
