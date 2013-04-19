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
package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.ide.impl.NewProjectUtil;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.JavaProjectData;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author Denis Zhdanov
 * @since 4/15/13 12:09 PM
 */
public class JavaProjectDataService implements ProjectDataService<JavaProjectData> {

  @NotNull
  @Override
  public Key<JavaProjectData> getTargetDataKey() {
    return ProjectKeys.JAVA_PROJECT;
  }

  @Override
  public void importData(@NotNull Collection<DataNode<JavaProjectData>> toImport, @NotNull Project project, boolean synchronous) {
    if (toImport.size() != 1) {
      throw new IllegalArgumentException(String.format("Expected to get a single project but got %d: %s", toImport.size(), toImport));
    }
    JavaProjectData projectData = toImport.iterator().next().getData();
    
    // JDK.
    JavaSdkVersion version = projectData.getJdkVersion();
    JavaSdk javaSdk = JavaSdk.getInstance();
    Sdk sdk = ProjectRootManagerEx.getInstanceEx(project).getProjectSdk();
    if (sdk instanceof JavaSdk) {
      JavaSdkVersion currentVersion = javaSdk.getVersion(sdk);
      if (currentVersion == null || !currentVersion.isAtLeast(version)) {
        Sdk newJdk = ExternalSystemUtil.findJdk(version);
        if (newJdk != null) {
          NewProjectUtil.applyJdkToProject(project, newJdk);
        }
      }
    }
    // Language level.
    setLanguageLevel(projectData.getLanguageLevel(), project, synchronous);
  }

  @Override
  public void removeData(@NotNull Collection<DataNode<JavaProjectData>> toRemove, @NotNull Project project, boolean synchronous) {
  }

  @SuppressWarnings("MethodMayBeStatic")
  public void setLanguageLevel(@NotNull final LanguageLevel languageLevel, @NotNull Project project, boolean synchronous) {
    final LanguageLevelProjectExtension languageLevelExtension = LanguageLevelProjectExtension.getInstance(project);
    if (languageLevelExtension.getLanguageLevel().isAtLeast(languageLevel)) {
      return;
    }
    ExternalSystemUtil.executeProjectChangeAction(project, ProjectSystemId.IDE, synchronous, new Runnable() {
      @Override
      public void run() {
        languageLevelExtension.setLanguageLevel(languageLevel);
      }
    });
  }

}
