// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema;

import com.intellij.execution.configurations.RuntimeConfigurationWarning;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.util.UriUtil;
import com.intellij.util.Urls;
import com.jetbrains.jsonSchema.impl.JsonSchemaReader;
import com.jetbrains.jsonSchema.remote.JsonFileResolver;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;

/**
 * @author Irina.Chernushina on 2/2/2016.
 */
public class JsonSchemaConfigurable extends NamedConfigurable<UserDefinedJsonSchemaConfiguration> {
  private final Project myProject;
  @NotNull private String mySchemaFilePath;
  @NotNull private final UserDefinedJsonSchemaConfiguration mySchema;
  @Nullable private final Runnable myTreeUpdater;
  private boolean myFresh;
  private JsonSchemaMappingsView myView;
  private String myDisplayName;
  private String myError;

  public JsonSchemaConfigurable(Project project,
                                @NotNull String schemaFilePath, @NotNull UserDefinedJsonSchemaConfiguration schema,
                                @Nullable Runnable updateTree,
                                boolean fresh) {
    super(true, updateTree);
    myProject = project;
    mySchemaFilePath = schemaFilePath;
    mySchema = schema;
    myTreeUpdater = updateTree;
    myFresh = fresh;
    myDisplayName = mySchema.getName();
  }

  @NotNull
  public UserDefinedJsonSchemaConfiguration getSchema() {
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
      myView = new JsonSchemaMappingsView(myProject, myTreeUpdater);
      myView.setError(myError);
    }
    return myView.getComponent();
  }

  @Nls
  @Override
  public String getDisplayName() {
    return myDisplayName;
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return JsonSchemaMappingsConfigurable.SETTINGS_JSON_SCHEMA;
  }

  @Override
  public boolean isModified() {
    if (myView == null) return false;
    if (!FileUtil.toSystemDependentName(mySchema.getRelativePathToSchema()).equals(myView.getSchemaSubPath())) return false;
    return !Comparing.equal(myView.getData(), mySchema.getPatterns());
  }

  @Override
  public void apply() throws ConfigurationException {
    if (myView == null) return;
    doValidation();
    mySchema.setName(myDisplayName);
    mySchema.setPatterns(myView.getData());
    mySchema.setRelativePathToSchema(myView.getSchemaSubPath());
  }

  public static boolean isHttpPath(@NotNull String schemaFieldText) {
    Couple<String> couple = UriUtil.splitScheme(schemaFieldText);
    return couple.first.startsWith("http");
  }

  public static boolean isValidURL(@NotNull final String url) {
    return isHttpPath(url) && Urls.parse(url, false) != null;
  }

  private void doValidation() throws ConfigurationException {
    String schemaSubPath = myView.getSchemaSubPath();

    if (StringUtil.isEmptyOrSpaces(schemaSubPath)) {
      throw new ConfigurationException((!StringUtil.isEmptyOrSpaces(myDisplayName) ? (myDisplayName + ": ") : "") + "Schema path is empty");
    }

    VirtualFile vFile;
    String filename;

    if (isHttpPath(schemaSubPath)) {
      filename = schemaSubPath;

      if (!isValidURL(schemaSubPath)) {
        throw new ConfigurationException(
          (!StringUtil.isEmptyOrSpaces(myDisplayName) ? (myDisplayName + ": ") : "") + "Invalid schema URL");
      }

      vFile = JsonFileResolver.urlToFile(schemaSubPath);
      if (vFile == null) {
        throw new ConfigurationException(
          (!StringUtil.isEmptyOrSpaces(myDisplayName) ? (myDisplayName + ": ") : "") + "Invalid URL resource");
      }
    }
    else {
      final File file = new File(myProject.getBasePath(), schemaSubPath);
      if (!file.exists() || (vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)) == null) {
        throw new ConfigurationException(
          (!StringUtil.isEmptyOrSpaces(myDisplayName) ? (myDisplayName + ": ") : "") + "Schema file does not exist");
      }
      filename = file.getName();
    }

    if (StringUtil.isEmptyOrSpaces(myDisplayName)) throw new ConfigurationException(filename + ": Schema name is empty");

    // we don't validate remote schemas while in options dialog
    if (vFile instanceof HttpVirtualFile) return;

    final String error = JsonSchemaReader.checkIfValidJsonSchema(myProject, vFile);
    if (error != null) {
      logErrorForUser(error);
      throw new RuntimeConfigurationWarning(error);
    }
  }

  private void logErrorForUser(@NotNull final String error) {
    JsonSchemaReader.ERRORS_NOTIFICATION.createNotification(error, MessageType.ERROR).notify(myProject);
  }

  @Override
  public void reset() {
    if (myView == null) return;
    myView.setItems(mySchemaFilePath, mySchema.getPatterns());
    setDisplayName(mySchema.getName());
  }

  public UserDefinedJsonSchemaConfiguration getUiSchema() {
    final UserDefinedJsonSchemaConfiguration info = new UserDefinedJsonSchemaConfiguration();
    info.setApplicationLevel(mySchema.isApplicationLevel());
    if (myView != null && myView.isInitialized()) {
      info.setName(getDisplayName());
      info.setPatterns(myView.getData());
      info.setRelativePathToSchema(myView.getSchemaSubPath());
    } else {
      info.setName(mySchema.getName());
      info.setPatterns(mySchema.getPatterns());
      info.setRelativePathToSchema(mySchema.getRelativePathToSchema());
    }
    return info;
  }

  @Override
  public void updateName() {
    if (myFresh) {
      myView.runFileChooser();
      String schemaSubPath = myView.getSchemaSubPath();
      if (schemaSubPath.isEmpty())  {
        myFresh = false;
        super.updateName();
        return;
      }
      File file = new File(myProject.getBasePath(), schemaSubPath);
      mySchemaFilePath = file.getPath();
      myDisplayName = FileUtil.getNameWithoutExtension(file);
      mySchema.setName(myDisplayName);
      myFresh = false;
    }
    super.updateName();
  }

  @Override
  public void disposeUIResources() {
    if (myView != null) Disposer.dispose(myView);
  }

  public void setError(String error) {
    myError = error;
    if (myView != null) {
      myView.setError(error);
    }
  }
}
