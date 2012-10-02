/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.platform.templates;

import com.intellij.ide.util.newProjectWizard.modes.ImportImlMode;
import com.intellij.ide.util.projectWizard.ExistingModuleLoader;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.templates.github.AbstractGithubTagDownloadedProjectGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Dmitry Avdeev
 *         Date: 10/1/12
 */
public class GithubBasedProjectTemplate extends AbstractGithubTagDownloadedProjectGenerator {
  private String myDisplayName;
  private String myGithubUserName;
  private String myGithubRepositoryName;
  private String myHomepageUrl;
  private String myDescription;
  private final WizardContext myContext;

  public GithubBasedProjectTemplate(String displayName,
                                    String githubRepositoryName,
                                    String homepageUrl,
                                    String description,
                                    WizardContext context) {
    myDisplayName = displayName;
    myGithubRepositoryName = githubRepositoryName;
    myHomepageUrl = homepageUrl;
    myDescription = description;
    myContext = context;
  }

  @NotNull
  @Override
  protected String getDisplayName() {
    return myDisplayName;
  }

  @Override
  protected String getGithubUserName() {
    return myGithubUserName;
  }

  @NotNull
  @Override
  protected String getGithubRepositoryName() {
    return myGithubRepositoryName;
  }

  @Override
  public String getHomepageUrl() {
    return myHomepageUrl;
  }

  @Override
  public String getDescription() {
    return myDescription;
  }

  @Override
  public ProjectBuilder createModuleBuilder() {
    final String path = myContext.getProjectFileDirectory() + "/empty-java.iml";
    final ExistingModuleLoader loader = ImportImlMode.setUpLoader(path);
    return new ProjectBuilder() {
      @Nullable
      @Override
      public List<Module> commit(Project project, ModifiableModuleModel model, ModulesProvider modulesProvider) {
        VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(myContext.getProjectFileDirectory());
        doGenerate(project, file, myPeer.getValue().getSettings());
        return loader.commit(project, model, modulesProvider);
      }
    };
  }
}
