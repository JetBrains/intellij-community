// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.conversion.impl;

import com.intellij.conversion.CannotConvertException;
import com.intellij.conversion.RunManagerSettings;
import com.intellij.execution.impl.RunManagerImplKt;
import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

final class RunManagerSettingsImpl implements RunManagerSettings {
  private static final String RUN_MANAGER_COMPONENT_NAME = "RunManager";
  private static final String CONFIGURATION_ELEMENT = "configuration";

  private final SettingsXmlFile myWorkspaceFile;
  private final @Nullable SettingsXmlFile myProjectFile;
  private final Path dir;
  private final ConversionContextImpl context;
  private @Nullable List<Path> sharedConfigurationFiles;

  RunManagerSettingsImpl(@NotNull SettingsXmlFile workspaceFile,
                         @Nullable SettingsXmlFile projectFile,
                         @Nullable Path dir,
                         @NotNull ConversionContextImpl context) throws CannotConvertException {
    myWorkspaceFile = workspaceFile;
    myProjectFile = projectFile;
    this.dir = dir;
    this.context = context;
  }

  private @NotNull List<Path> getSharedConfigurationFiles() {
    if (sharedConfigurationFiles == null) {
      if (dir == null) {
        sharedConfigurationFiles = Collections.emptyList();
      }
      else {
        sharedConfigurationFiles = MultiFilesSettings.getSettingsXmlFiles(dir);
      }
    }
    return sharedConfigurationFiles;
  }

  @Override
  public @NotNull Collection<Element> getRunConfigurations() {
    List<Element> result = new ArrayList<>();
    //noinspection CollectionAddAllCanBeReplacedWithConstructor
    result.addAll(JDOMUtil.getChildren(myWorkspaceFile.findComponent(RUN_MANAGER_COMPONENT_NAME), CONFIGURATION_ELEMENT));
    if (myProjectFile != null) {
      result.addAll(JDOMUtil.getChildren(myProjectFile.findComponent(RunManagerImplKt.PROJECT_RUN_MANAGER_COMPONENT_NAME), CONFIGURATION_ELEMENT));
    }

    for (Path file : getSharedConfigurationFiles()) {
      result.addAll(JDOMUtil.getChildren(context.getOrCreateFile(file).getRootElement(), CONFIGURATION_ELEMENT));
    }

    return result;
  }

  public void collectAffectedFiles(@NotNull Collection<Path> files) {
    files.add(myWorkspaceFile.getFile());
    if (myProjectFile != null) {
      files.add(myProjectFile.getFile());
    }
    files.addAll(getSharedConfigurationFiles());
  }
}
