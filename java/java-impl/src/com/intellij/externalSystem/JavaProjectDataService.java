/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.externalSystem;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService;
import com.intellij.openapi.externalSystem.util.DisposeAwareProjectChange;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author Denis Zhdanov
 * @since 4/15/13 12:09 PM
 */
public class JavaProjectDataService extends AbstractProjectDataService<JavaProjectData, Project> {
  @NotNull
  @Override
  public Key<JavaProjectData> getTargetDataKey() {
    return JavaProjectData.KEY;
  }

  @Override
  public void importData(@NotNull final Collection<DataNode<JavaProjectData>> toImport,
                         @Nullable final ProjectData projectData,
                         @NotNull final Project project,
                         @NotNull final IdeModifiableModelsProvider modelsProvider) {
    if (toImport.isEmpty() || projectData == null) {
      return;
    }

    if (toImport.size() != 1) {
      throw new IllegalArgumentException(String.format("Expected to get a single project but got %d: %s", toImport.size(), toImport));
    }

    final DataNode<JavaProjectData> javaProjectDataNode = toImport.iterator().next();
    final DataNode<ProjectData> projectDataNode = ExternalSystemApiUtil.findParent(javaProjectDataNode, ProjectKeys.PROJECT);

    assert projectDataNode != null;
    if (!ExternalSystemApiUtil.isOneToOneMapping(project, projectDataNode.getData())) {
      return;
    }

    JavaProjectData javaProjectData = javaProjectDataNode.getData();

    // JDK.
    JavaSdkVersion version = javaProjectData.getJdkVersion();
    JavaSdk javaSdk = JavaSdk.getInstance();
    ProjectRootManager rootManager = ProjectRootManager.getInstance(project);
    Sdk sdk = rootManager.getProjectSdk();
    if(sdk != null) {
      JavaSdkVersion currentVersion = javaSdk.getVersion(sdk);
      if (currentVersion == null || !currentVersion.isAtLeast(version)) {
        updateSdk(project, version);
      }
    } else {
      updateSdk(project, version);
    }

    // Language level.
    setLanguageLevel(javaProjectData.getLanguageLevel(), project);
  }

  private static void updateSdk(@NotNull final Project project, @NotNull final JavaSdkVersion version) {
    Sdk sdk = JavaSdkVersionUtil.findJdkByVersion(version);
    if (sdk == null) return;

    ExternalSystemApiUtil.executeProjectChangeAction(new DisposeAwareProjectChange(project) {
      @Override
      public void execute() {
        ProjectRootManager.getInstance(project).setProjectSdk(sdk);
        LanguageLevel level = version.getMaxLanguageLevel();
        LanguageLevelProjectExtension languageLevelExtension = LanguageLevelProjectExtension.getInstance(project);
        if (level.compareTo(languageLevelExtension.getLanguageLevel()) < 0) {
          languageLevelExtension.setLanguageLevel(level);
        }
      }
    });
  }

  @SuppressWarnings("MethodMayBeStatic")
  public void setLanguageLevel(@NotNull final LanguageLevel languageLevel, @NotNull Project project) {
    final LanguageLevelProjectExtension languageLevelExtension = LanguageLevelProjectExtension.getInstance(project);
    if (languageLevelExtension.getLanguageLevel().isAtLeast(languageLevel)) {
      return;
    }
    ExternalSystemApiUtil.executeProjectChangeAction(new DisposeAwareProjectChange(project) {
      @Override
      public void execute() {
        languageLevelExtension.setLanguageLevel(languageLevel);
      }
    });
  }
}