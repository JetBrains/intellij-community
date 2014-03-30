/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.conversion.impl;

import com.intellij.conversion.CannotConvertException;
import com.intellij.conversion.RunManagerSettings;
import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author nik
 */
public class RunManagerSettingsImpl implements RunManagerSettings {
  @NonNls public static final String RUN_MANAGER_COMPONENT_NAME = "RunManager";
  @NonNls private static final String PROJECT_RUN_MANAGER = "ProjectRunConfigurationManager";
  @NonNls public static final String CONFIGURATION_ELEMENT = "configuration";
  private SettingsXmlFile myWorkspaceFile;
  private SettingsXmlFile myProjectFile;
  private final List<SettingsXmlFile> mySharedConfigurationFiles;

  public RunManagerSettingsImpl(@NotNull File workspaceFile, @Nullable File projectFile, @Nullable File[] sharedConfigurationFiles,
                                ConversionContextImpl context) throws CannotConvertException {
    if (workspaceFile.exists()) {
      myWorkspaceFile = context.getOrCreateFile(workspaceFile);
    }

    if (projectFile != null && projectFile.exists()) {
      myProjectFile = context.getOrCreateFile(projectFile);
    }

    mySharedConfigurationFiles = new ArrayList<SettingsXmlFile>();
    if (sharedConfigurationFiles != null) {
      for (File file : sharedConfigurationFiles) {
        mySharedConfigurationFiles.add(context.getOrCreateFile(file));
      }
    }
  }

  @Override
  @NotNull
  public Collection<? extends Element> getRunConfigurations() {
    final List<Element> result = new ArrayList<Element>();
    if (myWorkspaceFile != null) {
      result.addAll(JDOMUtil.getChildren(myWorkspaceFile.findComponent(RUN_MANAGER_COMPONENT_NAME), CONFIGURATION_ELEMENT));
    }

    if (myProjectFile != null) {
      result.addAll(JDOMUtil.getChildren(myProjectFile.findComponent(PROJECT_RUN_MANAGER), CONFIGURATION_ELEMENT));
    }

    for (SettingsXmlFile file : mySharedConfigurationFiles) {
      result.addAll(JDOMUtil.getChildren(file.getRootElement(), CONFIGURATION_ELEMENT));
    }

    return result;
  }

  public Collection<File> getAffectedFiles() {
    final List<File> files = new ArrayList<File>();
    if (myWorkspaceFile != null) {
      files.add(myWorkspaceFile.getFile());
    }
    if (myProjectFile != null) {
      files.add(myProjectFile.getFile());
    }
    for (SettingsXmlFile file : mySharedConfigurationFiles) {
      files.add(file.getFile());
    }
    return files;
  }

}
