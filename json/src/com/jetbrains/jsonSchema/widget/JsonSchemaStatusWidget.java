// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.widget;

import com.intellij.icons.AllIcons;
import com.intellij.json.JsonLanguage;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.http.FileDownloadingAdapter;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.openapi.vfs.impl.http.RemoteFileInfo;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup;
import com.jetbrains.jsonSchema.extension.JsonSchemaEnabler;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaInfo;
import com.jetbrains.jsonSchema.extension.SchemaType;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaConflictNotificationProvider;
import com.jetbrains.jsonSchema.impl.JsonSchemaServiceImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

class JsonSchemaStatusWidget extends EditorBasedStatusBarPopup {
  private static final String JSON_SCHEMA_BAR = "JSON: ";
  private static final String JSON_SCHEMA_TOOLTIP = "JSON Schema: ";
  private final JsonSchemaService myService;
  private static final String ID = "JSONSchemaSelector";

  public JsonSchemaStatusWidget(Project project) {
    super(project);
    myService = JsonSchemaService.Impl.get(project);
    myService.registerRemoteUpdateCallback(myUpdateCallback);
    myService.registerResetAction(myUpdateCallback);
  }

  private final Runnable myUpdateCallback = this::update;

  private static class MyWidgetState extends WidgetState {
    boolean warning = false;
    public MyWidgetState(String toolTip, String text, boolean actionEnabled) {
      super(toolTip, text, actionEnabled);
    }

    public boolean isWarning() {
      return warning;
    }

    public void setWarning(boolean warning) {
      this.warning = warning;
      this.setIcon(warning ? AllIcons.General.Warning : null);
    }
  }

  private boolean hasAccessToSymbols() {
    return !DumbService.getInstance(myProject).isDumb();
  }

  @NotNull
  @Override
  protected WidgetState getWidgetState(@Nullable VirtualFile file) {
    if (file == null) {
      return WidgetState.HIDDEN;
    }

    JsonSchemaEnabler[] enablers = JsonSchemaEnabler.EXTENSION_POINT_NAME.getExtensions();
    if (Arrays.stream(enablers).noneMatch(e -> e.isEnabledForFile(file) && e.shouldShowSwitcherWidget(file))) {
      return WidgetState.HIDDEN;
    }

    if (!hasAccessToSymbols()) {
      return WidgetState.getDumbModeState("JSON schema service", "JSON: ");
    }

    Collection<VirtualFile> schemaFiles = myService.getSchemaFilesForFile(file);
    if (schemaFiles.size() == 0) {
      return getNoSchemaState();
    }

    if (schemaFiles.size() != 1) {
      List<VirtualFile> onlyUserSchemas = schemaFiles.stream().filter(s -> {
        JsonSchemaFileProvider provider = myService.getSchemaProvider(s);
        return provider != null && provider.getSchemaType() == SchemaType.userSchema;
      }).collect(Collectors.toList());
      if (onlyUserSchemas.size() > 1) {
        MyWidgetState state = new MyWidgetState(JsonSchemaConflictNotificationProvider.createMessage(schemaFiles, myService,
                                                                                                     "<br/>", "Conflicting schemas:<br/>",
                                                                                                     ""),
                                                schemaFiles.size() + " schemas (!)", true);
        state.setWarning(true);
        return state;
      }
      schemaFiles = onlyUserSchemas;
      if (schemaFiles.size() == 0) {
        return getNoSchemaState();
      }
    }

    VirtualFile schemaFile = schemaFiles.iterator().next();
    schemaFile = ((JsonSchemaServiceImpl)myService).replaceHttpFileWithBuiltinIfNeeded(schemaFile);

    if (schemaFile instanceof HttpVirtualFile) {
      RemoteFileInfo info = ((HttpVirtualFile)schemaFile).getFileInfo();
      if (info == null) return getDownloadErrorState(null);

      //noinspection EnumSwitchStatementWhichMissesCases
      switch (info.getState()) {
        case DOWNLOADING_NOT_STARTED:
        case DOWNLOADING_IN_PROGRESS:
          info.addDownloadingListener(new FileDownloadingAdapter() {
            @Override
            public void fileDownloaded(VirtualFile localFile) {
              update();
            }

            @Override
            public void errorOccurred(@NotNull String errorMessage) {
              update();
            }

            @Override
            public void downloadingCancelled() {
              update();
            }
          });
          return new MyWidgetState("Download is scheduled or in progress", "Downloading JSON schema", false);
        case ERROR_OCCURRED:
          return getDownloadErrorState(info.getErrorMessage());
      }
    }

    if (!isValidSchemaFile(schemaFile)) {
      MyWidgetState state = new MyWidgetState("File is not a schema", "JSON schema error", true);
      state.setWarning(true);
      return state;
    }

    JsonSchemaFileProvider provider = myService.getSchemaProvider(schemaFile);
    if (provider != null) {
      String providerName = provider.getPresentableName();
      String shortName = StringUtil.trimEnd(StringUtil.trimEnd(providerName, ".json"), "-schema");
      String name = shortName.startsWith("JSON schema") ? shortName : (JSON_SCHEMA_BAR + shortName);
      String kind = provider.getSchemaType() == SchemaType.embeddedSchema || provider.getSchemaType() == SchemaType.schema ? " (bundled)" : "";
      return new MyWidgetState(JSON_SCHEMA_TOOLTIP + providerName + kind, name, true);
    }

    return new MyWidgetState(JSON_SCHEMA_TOOLTIP + getSchemaFileDesc(schemaFile), JSON_SCHEMA_BAR + getPresentableNameForFile(schemaFile),
                             true);
  }

  private boolean isValidSchemaFile(VirtualFile schemaFile) {
    if (schemaFile == null || !myService.isApplicableToFile(schemaFile) || !myService.isSchemaFile(schemaFile)) return false;
    FileType type = schemaFile.getFileType();
    return type instanceof LanguageFileType && ((LanguageFileType)type).getLanguage() instanceof JsonLanguage;
  }

  @Nullable
  private static String extractNpmPackageName(@Nullable String path) {
    if (path == null) return null;
    int idx = path.indexOf("node_modules");
    if (idx != -1) {
      int trimIndex = idx + "node_modules".length() + 1;
      if (trimIndex < path.length()) {
        path = path.substring(trimIndex);
        idx = StringUtil.indexOfAny(path, "\\/");
        if (idx != -1) {
          if (path.startsWith("@")) {
            idx = StringUtil.indexOfAny(path, "\\/", idx + 1, path.length());
          }
        }

        if (idx != -1) {
          return path.substring(0, idx);
        }
      }
    }
    return null;
  }

  @NotNull
  private static String getPresentableNameForFile(@NotNull VirtualFile schemaFile) {
    if (schemaFile instanceof HttpVirtualFile) {
      return new JsonSchemaInfo(schemaFile.getUrl()).getDescription();
    }

    String nameWithoutExtension = schemaFile.getNameWithoutExtension();
    if (!JsonSchemaInfo.isVeryDumbName(nameWithoutExtension)) return nameWithoutExtension;

    String path = schemaFile.getPath();

    String npmPackageName = extractNpmPackageName(path);
    return npmPackageName != null ? npmPackageName : schemaFile.getName();
  }

  @NotNull
  private static WidgetState getDownloadErrorState(@Nullable String message) {
    MyWidgetState state = new MyWidgetState("Error downloading schema" + (message == null ? "" : (": <br/>" + message)),
                                            "JSON schema error", true);
    state.setWarning(true);
    return state;
  }

  @NotNull
  private static WidgetState getNoSchemaState() {
    return new MyWidgetState("No JSON Schema defined", "No JSON schema", true);
  }

  @NotNull
  private static String getSchemaFileDesc(@NotNull VirtualFile schemaFile) {
    if (schemaFile instanceof HttpVirtualFile) {
      return schemaFile.getPresentableUrl();
    }

    String npmPackageName = extractNpmPackageName(schemaFile.getPath());
    return schemaFile.getName() + (npmPackageName == null ? "" : (" (Package: " + npmPackageName + ")"));
  }

  @Nullable
  @Override
  protected ListPopup createPopup(DataContext context) {
    final VirtualFile virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(context);
    if (virtualFile == null) return null;

    Project project = getProject();
    if (project == null) return null;
    WidgetState state = getWidgetState(virtualFile);
    if (!(state instanceof MyWidgetState)) return null;
    return doCreatePopup(virtualFile, project, ((MyWidgetState)state).isWarning());
  }

  @NotNull
  private ListPopup doCreatePopup(@NotNull VirtualFile virtualFile, @NotNull Project project, boolean showOnlyEdit) {
    return JsonSchemaStatusPopup.createPopup(myService, project, virtualFile, showOnlyEdit);
  }

  @Override
  protected void registerCustomListeners() {
    class Listener implements DumbService.DumbModeListener {
      volatile boolean isDumbMode;

      @Override
      public void enteredDumbMode() {
        isDumbMode = true;
        update();
      }

      @Override
      public void exitDumbMode() {
        isDumbMode = false;
        update();
      }
    }

    Listener listener = new Listener();
    myConnection.subscribe(DumbService.DUMB_MODE, listener);
  }

  @NotNull
  @Override
  protected StatusBarWidget createInstance(Project project) {
    return new JsonSchemaStatusWidget(project);
  }

  @NotNull
  @Override
  public String ID() {
    return ID;
  }

  @Override
  public void dispose() {
    myService.unregisterRemoteUpdateCallback(myUpdateCallback);
    myService.unregisterResetAction(myUpdateCallback);
    super.dispose();
  }
}
