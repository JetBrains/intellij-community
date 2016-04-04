/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

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
    final Sdk sdk = findJdk(version);
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

  @Nullable
  private static Sdk findJdk(@NotNull JavaSdkVersion version) {
    JavaSdk javaSdk = JavaSdk.getInstance();
    List<Sdk> javaSdks = ProjectJdkTable.getInstance().getSdksOfType(javaSdk);
    Sdk candidate = null;
    for (Sdk sdk : javaSdks) {
      JavaSdkVersion v = javaSdk.getVersion(sdk);
      if (v == version) {
        return sdk;
      }
      else if (candidate == null && v != null && version.getMaxLanguageLevel().isAtLeast(version.getMaxLanguageLevel())) {
        candidate = sdk;
      }
    }
    return candidate;
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
