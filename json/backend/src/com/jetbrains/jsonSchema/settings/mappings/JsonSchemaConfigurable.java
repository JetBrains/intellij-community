// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.settings.mappings;

import com.intellij.execution.configurations.RuntimeConfigurationWarning;
import com.intellij.json.JsonBundle;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts.ConfigurableName;
import com.intellij.openapi.util.NlsContexts.NotificationContent;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.util.Function;
import com.intellij.util.Urls;
import com.jetbrains.jsonSchema.UserDefinedJsonSchemaConfiguration;
import com.jetbrains.jsonSchema.impl.JsonSchemaReader;
import com.jetbrains.jsonSchema.remote.JsonFileResolver;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;

public final class JsonSchemaConfigurable extends NamedConfigurable<UserDefinedJsonSchemaConfiguration> {
  private final Project myProject;
  private final @NotNull String mySchemaFilePath;
  private final @NotNull UserDefinedJsonSchemaConfiguration mySchema;
  private final @Nullable TreeUpdater myTreeUpdater;
  private final @NotNull Function<? super String, String> myNameCreator;
  private JsonSchemaMappingsView myView;
  private @ConfigurableName String myDisplayName;
  private @Nls String myError;

  public JsonSchemaConfigurable(Project project,
                                @NotNull String schemaFilePath, @NotNull UserDefinedJsonSchemaConfiguration schema,
                                @Nullable TreeUpdater updateTree,
                                @NotNull Function<? super String, String> nameCreator) {
    super(true, () -> {
      if (updateTree != null) {
        updateTree.updateTree(true);
      }
    });
    myProject = project;
    mySchemaFilePath = schemaFilePath;
    mySchema = schema;
    myTreeUpdater = updateTree;
    myNameCreator = nameCreator;
    myDisplayName = mySchema.getName();
  }

  public @NotNull UserDefinedJsonSchemaConfiguration getSchema() {
    return mySchema;
  }

  @Override
  public void setDisplayName(String name) {
    myDisplayName = name;
  }

  @Override
  public UserDefinedJsonSchemaConfiguration getEditableObject() {
    return mySchema;
  }

  @Override
  public String getBannerSlogan() {
    return mySchema.getName();
  }

  @Override
  public JComponent createOptionsPanel() {
    if (myView == null) {
      myView = new JsonSchemaMappingsView(myProject, myTreeUpdater, (s, force) -> {
        if (force || isGeneratedName()) {
          int lastSlash = Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\'));
          if (lastSlash > 0 || force) {
            String substring = lastSlash > 0 ? s.substring(lastSlash + 1) : s;
            int dot = lastSlash > 0 ? substring.lastIndexOf('.') : -1;
            if (dot != -1) {
              substring = substring.substring(0, dot);
            }
            setDisplayName(myNameCreator.fun(substring));
            updateName();
          }
        }
      });
      myView.setError(myError, true);
    }
    return myView.getComponent();
  }

  private boolean isGeneratedName() {
    return myDisplayName.equals(mySchema.getName()) && myDisplayName.equals(mySchema.getGeneratedName());
  }

  @Override
  public @Nls String getDisplayName() {
    return myDisplayName;
  }

  @Override
  public @NotNull String getHelpTopic() {
    return JsonSchemaMappingsConfigurable.SETTINGS_JSON_SCHEMA;
  }

  @Override
  public boolean isModified() {
    if (myView == null) return false;
    if (!FileUtil.toSystemDependentName(mySchema.getRelativePathToSchema()).equals(myView.getSchemaSubPath())) return true;
    if (mySchema.getSchemaVersion() != myView.getSchemaVersion()) return true;
    return !Comparing.equal(myView.getData(), mySchema.getPatterns());
  }

  @Override
  public void apply() throws ConfigurationException {
    if (myView == null) return;
    doValidation();
    mySchema.setName(myDisplayName);
    mySchema.setSchemaVersion(myView.getSchemaVersion());
    mySchema.setPatterns(myView.getData());
    mySchema.setRelativePathToSchema(myView.getSchemaSubPath());
  }

  public static boolean isValidURL(final @NotNull String url) {
    return JsonFileResolver.isHttpPath(url) && Urls.parse(url, false) != null;
  }

  private void doValidation() throws ConfigurationException {
    String schemaSubPath = myView.getSchemaSubPath();

    if (StringUtil.isEmptyOrSpaces(schemaSubPath)) {
      throw new ConfigurationException((!StringUtil.isEmptyOrSpaces(myDisplayName) ? (myDisplayName + ": ") : "") + JsonBundle.message("schema.configuration.error.empty.path"));
    }

    VirtualFile vFile;
    String filename;

    if (JsonFileResolver.isHttpPath(schemaSubPath)) {
      filename = schemaSubPath;

      if (!isValidURL(schemaSubPath)) {
        throw new ConfigurationException(
          (!StringUtil.isEmptyOrSpaces(myDisplayName) ? (myDisplayName + ": ") : "") + JsonBundle.message("schema.configuration.error.invalid.url"));
      }

      vFile = JsonFileResolver.urlToFile(schemaSubPath);
      if (vFile == null) {
        throw new ConfigurationException(
          (!StringUtil.isEmptyOrSpaces(myDisplayName) ? (myDisplayName + ": ") : "") + JsonBundle.message("schema.configuration.error.invalid.url.resource"));
      }
    }
    else {
      File subPath = new File(schemaSubPath);
      final File file = subPath.isAbsolute() ? subPath : new File(myProject.getBasePath(), schemaSubPath);
      if (!file.exists() || (vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)) == null) {
        throw new ConfigurationException(
          (!StringUtil.isEmptyOrSpaces(myDisplayName) ? (myDisplayName + ": ") : "") + JsonBundle.message("schema.configuration.error.file.does.not.exist"));
      }
      filename = file.getName();
    }

    if (StringUtil.isEmptyOrSpaces(myDisplayName)) throw new ConfigurationException(filename + ": " + JsonBundle.message("schema.configuration.error.empty.name"));

    // we don't validate remote schemas while in options dialog
    if (vFile instanceof HttpVirtualFile) return;

    final String error = JsonSchemaReader.checkIfValidJsonSchema(myProject, vFile);
    if (error != null) {
      logErrorForUser(error);
      throw new RuntimeConfigurationWarning(error);
    }
  }

  private void logErrorForUser(@NotNull @NotificationContent String error) {
    NotificationGroupManager.getInstance()
      .getNotificationGroup("JSON Schema")
      .createNotification(error, MessageType.WARNING).notify(myProject);
  }

  @Override
  public void reset() {
    if (myView == null) return;
    myView.setItems(mySchemaFilePath, mySchema.getSchemaVersion(), mySchema.getPatterns());
    setDisplayName(mySchema.getName());
  }

  public UserDefinedJsonSchemaConfiguration getUiSchema() {
    final UserDefinedJsonSchemaConfiguration info = new UserDefinedJsonSchemaConfiguration();
    info.setApplicationDefined(mySchema.isApplicationDefined());
    if (myView != null && myView.isInitialized()) {
      info.setName(getDisplayName());
      info.setSchemaVersion(myView.getSchemaVersion());
      info.setPatterns(myView.getData());
      info.setRelativePathToSchema(myView.getSchemaSubPath());
    } else {
      info.setName(mySchema.getName());
      info.setSchemaVersion(mySchema.getSchemaVersion());
      info.setPatterns(mySchema.getPatterns());
      info.setRelativePathToSchema(mySchema.getRelativePathToSchema());
    }
    return info;
  }

  @Override
  public void disposeUIResources() {
    if (myView != null) Disposer.dispose(myView);
  }

  public void setError(@Nls String error, boolean showWarning) {
    myError = error;
    if (myView != null) {
      myView.setError(error, showWarning);
    }
  }
}
