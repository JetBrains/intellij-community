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
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.platform.ProjectTemplate;
import com.intellij.platform.templates.github.ZipUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.zip.ZipInputStream;

/**
 * @author Dmitry Avdeev
 *         Date: 10/1/12
 */
public class ArchivedProjectTemplate implements ProjectTemplate {
  private final String myDisplayName;
  private final String myDescription;
  private final String myArchivePath;
  private final ClassLoader myResourceLoader;
  private final WizardContext myContext;

  public ArchivedProjectTemplate(String displayName,
                                 String description,
                                 String archivePath,
                                 ClassLoader resourceLoader,
                                 WizardContext context) {

    myDisplayName = displayName;
    myDescription = description;
    myArchivePath = archivePath;
    myResourceLoader = resourceLoader;
    myContext = context;
  }

  @Override
  public String getName() {
    return myDisplayName;
  }

  @Override
  public String getDescription() {
    return myDescription;
  }

  @NotNull
  @Override
  public ProjectBuilder createModuleBuilder() {
    return new ProjectBuilder() {
      @Nullable
      @Override
      public List<Module> commit(Project project, ModifiableModuleModel model, ModulesProvider modulesProvider) {
        InputStream stream = myResourceLoader.getResourceAsStream(myArchivePath);
        if (stream == null) {
          throw new RuntimeException("Can't open " + myArchivePath);
        }
        final String path = myContext.getProjectFileDirectory();
        try {
          ZipUtil.unzip(null, new File(path), new ZipInputStream(stream));
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
        return ImportImlMode.setUpLoader(path).commit(project, model, modulesProvider);
      }
    };
  }

  @Override
  public JComponent getSettingsPanel() {
    return null;
  }
}
