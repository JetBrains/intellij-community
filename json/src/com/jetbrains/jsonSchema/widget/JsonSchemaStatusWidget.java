// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.widget;

import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.icons.AllIcons;
import com.intellij.json.JsonLanguage;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.http.FileDownloadingAdapter;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.openapi.vfs.impl.http.RemoteFileInfo;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.SynchronizedClearableLazy;
import com.jetbrains.jsonSchema.JsonSchemaCatalogProjectConfiguration;
import com.jetbrains.jsonSchema.extension.*;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaServiceImpl;
import com.jetbrains.jsonSchema.remote.JsonFileResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

class JsonSchemaStatusWidget extends EditorBasedStatusBarPopup {
  private static final String JSON_SCHEMA_BAR = "JSON: ";
  private static final String JSON_SCHEMA_BAR_OTHER_FILES = "Schema: ";
  private static final String JSON_SCHEMA_TOOLTIP = "JSON Schema: ";
  private static final String JSON_SCHEMA_TOOLTIP_OTHER_FILES = "Validated by JSON Schema: ";
  private final SynchronizedClearableLazy<JsonSchemaService> myServiceLazy;
  private static final String ID = "JSONSchemaSelector";
  private static final AtomicBoolean myIsNotified = new AtomicBoolean(false);

  JsonSchemaStatusWidget(Project project) {
    super(project, false);
    myServiceLazy = new SynchronizedClearableLazy<>(() -> {
      if (!project.isDisposed()) {
        JsonSchemaService myService = JsonSchemaService.Impl.get(project);
        myService.registerRemoteUpdateCallback(myUpdateCallback);
        myService.registerResetAction(myUpdateCallback);
        return myService;
      }
      return null;
    });
  }

  @Nullable
  private JsonSchemaService getService() {
    return myServiceLazy.getValue();
  }

  private final Runnable myUpdateCallback = () -> {
    update();
    myIsNotified.set(false);
  };

  private static class MyWidgetState extends WidgetState {
    boolean warning = false;
    boolean conflict = false;
    MyWidgetState(String toolTip, String text, boolean actionEnabled) {
      super(toolTip, text, actionEnabled);
    }

    public boolean isWarning() {
      return warning;
    }

    public void setWarning(boolean warning) {
      this.warning = warning;
      this.setIcon(warning ? AllIcons.General.Warning : null);
    }

    private void setConflict() {
      this.conflict = true;
    }

    private String getTooltip() {
      return this.toolTip;
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

    List<JsonSchemaEnabler> enablers = JsonSchemaEnabler.EXTENSION_POINT_NAME.getExtensionList();
    if (enablers.stream().noneMatch(e -> e.isEnabledForFile(file) && e.shouldShowSwitcherWidget(file))) {
      return WidgetState.HIDDEN;
    }

    FileType fileType = file.getFileType();
    Language language = fileType instanceof LanguageFileType ? ((LanguageFileType)fileType).getLanguage() : null;
    boolean isJsonFile = language instanceof JsonLanguage;

    if (!hasAccessToSymbols()) {
      return WidgetState.getDumbModeState("JSON schema service", isJsonFile ? JSON_SCHEMA_BAR : JSON_SCHEMA_BAR_OTHER_FILES);
    }

    List<JsonWidgetSuppressor> suppressors = JsonWidgetSuppressor.EXTENSION_POINT_NAME.getExtensionList();
    if (suppressors.stream().anyMatch(s -> s.suppressSwitcherWidget(file, myProject))) {
      return WidgetState.HIDDEN;
    }

    JsonSchemaService service = getService();
    if (service == null) {
      return getNoSchemaState();
    }
    Collection<VirtualFile> schemaFiles = service.getSchemaFilesForFile(file);
    if (schemaFiles.size() == 0) {
      return getNoSchemaState();
    }

    if (schemaFiles.size() != 1) {
      final List<VirtualFile> userSchemas = new ArrayList<>();
      if (hasConflicts(userSchemas, service, file)) {
        MyWidgetState state = new MyWidgetState(createMessage(schemaFiles, service,
                                                                                                     "<br/>", "There are several JSON Schemas mapped to this file:<br/>",
                                                                                                     ""),
                                                schemaFiles.size() + " schemas (!)", true);
        state.setWarning(true);
        state.setConflict();
        return state;
      }
      schemaFiles = userSchemas;
      if (schemaFiles.size() == 0) {
        return getNoSchemaState();
      }
    }

    VirtualFile schemaFile = schemaFiles.iterator().next();
    schemaFile = ((JsonSchemaServiceImpl)service).replaceHttpFileWithBuiltinIfNeeded(schemaFile);

    String tooltip = isJsonFile ? JSON_SCHEMA_TOOLTIP : JSON_SCHEMA_TOOLTIP_OTHER_FILES;
    String bar = isJsonFile ? JSON_SCHEMA_BAR : JSON_SCHEMA_BAR_OTHER_FILES;

    if (schemaFile instanceof HttpVirtualFile) {
      RemoteFileInfo info = ((HttpVirtualFile)schemaFile).getFileInfo();
      if (info == null) return getDownloadErrorState(null);

      //noinspection EnumSwitchStatementWhichMissesCases
      switch (info.getState()) {
        case DOWNLOADING_NOT_STARTED:
          addDownloadingUpdateListener(info);
          return new MyWidgetState(tooltip + getSchemaFileDesc(schemaFile), bar + getPresentableNameForFile(schemaFile),
                                   true);
        case DOWNLOADING_IN_PROGRESS:
          addDownloadingUpdateListener(info);
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

    JsonSchemaFileProvider provider = service.getSchemaProvider(schemaFile);
    if (provider != null) {
      final boolean preferRemoteSchemas = JsonSchemaCatalogProjectConfiguration.getInstance(myProject).isPreferRemoteSchemas();
      final String remoteSource = provider.getRemoteSource();
      boolean useRemoteSource = preferRemoteSchemas && remoteSource != null
                  && !JsonFileResolver.isSchemaUrl(remoteSource)
                  && !remoteSource.endsWith("!");
      String providerName = useRemoteSource ? remoteSource : provider.getPresentableName();
      String shortName = StringUtil.trimEnd(StringUtil.trimEnd(providerName, ".json"), "-schema");
      String name = useRemoteSource ? bar + new JsonSchemaInfo(remoteSource).getDescription() : (shortName.startsWith("JSON schema") ? shortName : (bar + shortName));
      String kind = !useRemoteSource && (provider.getSchemaType() == SchemaType.embeddedSchema || provider.getSchemaType() == SchemaType.schema)
                    ? " (bundled)"
                    : "";
      return new MyWidgetState(tooltip + providerName + kind, name, true);
    }

    return new MyWidgetState(tooltip + getSchemaFileDesc(schemaFile), bar + getPresentableNameForFile(schemaFile),
                             true);
  }

  private void addDownloadingUpdateListener(@NotNull RemoteFileInfo info) {
    info.addDownloadingListener(new FileDownloadingAdapter() {
      @Override
      public void fileDownloaded(@NotNull VirtualFile localFile) {
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
  }

  private boolean isValidSchemaFile(@Nullable VirtualFile schemaFile) {
    if (schemaFile == null) return false;
    JsonSchemaService service = getService();
    return service != null && service.isSchemaFile(schemaFile) && service.isApplicableToFile(schemaFile);
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
    WidgetState state = getWidgetState(virtualFile);
    if (!(state instanceof MyWidgetState)) return null;
    JsonSchemaService service = getService();
    if (service == null) return null;
    return JsonSchemaStatusPopup.createPopup(service, project, virtualFile, ((MyWidgetState)state).isWarning());
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

  @Override
  protected void handleFileChange(VirtualFile file) {
    myIsNotified.set(false);
  }

  @NotNull
  @Override
  protected StatusBarWidget createInstance(@NotNull Project project) {
    return new JsonSchemaStatusWidget(project);
  }

  @NotNull
  @Override
  public String ID() {
    return ID;
  }

  @Override
  public void dispose() {
    JsonSchemaService service = myServiceLazy.isInitialized() ? myServiceLazy.getValue() : null;
    if (service != null) {
      service.unregisterRemoteUpdateCallback(myUpdateCallback);
      service.unregisterResetAction(myUpdateCallback);
    }

    super.dispose();
  }

  @SuppressWarnings("SameParameterValue")
  private static String createMessage(@NotNull final Collection<? extends VirtualFile> schemaFiles,
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
    return pairList.stream().map(pair -> formatName(withTypes, pair)).collect(Collectors.joining(separator, prefix, suffix));
  }

  private static String formatName(boolean withTypes, Pair<Boolean, String> pair) {
    return "&nbsp;&nbsp;- " + (withTypes
           ? String.format("%s schema '%s'", Boolean.TRUE.equals(pair.getFirst()) ? "user" : "system", pair.getSecond())
           : pair.getSecond());
  }

  private static boolean hasConflicts(@NotNull Collection<VirtualFile> files,
                                      @NotNull JsonSchemaService service,
                                      @NotNull VirtualFile file) {
    List<JsonSchemaFileProvider> providers = ((JsonSchemaServiceImpl)service).getProvidersForFile(file);
    for (JsonSchemaFileProvider provider : providers) {
      if (provider.getSchemaType() != SchemaType.userSchema) continue;
      VirtualFile schemaFile = provider.getSchemaFile();
      if (schemaFile != null) {
        files.add(schemaFile);
      }
    }
    return files.size() > 1;
  }

  @Override
  protected void afterVisibleUpdate(@NotNull WidgetState state) {
    if (!(state instanceof MyWidgetState) || !((MyWidgetState)state).conflict) {
      myIsNotified.set(false);
      return;
    }
    if (myIsNotified.get()) return;

    myIsNotified.set(true);
    Alarm alarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);
    alarm.addRequest(() -> {
      final JComponent label =
        HintUtil.createErrorLabel("<b>JSON Schema conflicting mappings</b><br/><br/>" + ((MyWidgetState)state).getTooltip());
      BalloonBuilder builder = JBPopupFactory.getInstance().createBalloonBuilder(label);
      JComponent statusBarComponent = getComponent();
      Balloon balloon = builder
        .setCalloutShift(statusBarComponent.getHeight() / 2)
        .setDisposable(this)
        .setFillColor(HintUtil.getErrorColor())
        .setHideOnClickOutside(true)
        .createBalloon();
      balloon.showInCenterOf(statusBarComponent);
    }, 500, ModalityState.NON_MODAL);
  }
}
