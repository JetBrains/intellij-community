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
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.*;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.platform.templates.github.ZipUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.zip.ZipInputStream;

/**
* @author Dmitry Avdeev
*         Date: 10/19/12
*/
public class TemplateModuleBuilder extends ModuleBuilder {

  private static final NullableFunction<String,String> PATH_CONVERTOR = new NullableFunction<String, String>() {
    @Nullable
    @Override
    public String fun(String s) {
      return s.contains(".idea") ? null : s;
    }
  };

  private final ModuleType myType;
  private ArchivedProjectTemplate myTemplate;
  private boolean myProjectMode;

  public TemplateModuleBuilder(ArchivedProjectTemplate template, ModuleType moduleType) {
    myTemplate = template;
    myType = moduleType;
  }

  @Override
  public void setupRootModel(ModifiableRootModel modifiableRootModel) throws ConfigurationException {

  }

  @Override
  public ModuleWizardStep[] createWizardSteps(WizardContext wizardContext, ModulesProvider modulesProvider) {
    return myType.createModuleBuilder().createWizardSteps(wizardContext, modulesProvider);
  }

  @Override
  public Module commitModule(@NotNull final Project project, ModifiableModuleModel model) {
    if (myProjectMode) {
      final Module[] modules = ModuleManager.getInstance(project).getModules();
      if (modules.length > 0) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            try {
              Module module = modules[0];
              setupModule(module);
              ModifiableModuleModel modifiableModuleModel = ModuleManager.getInstance(project).getModifiableModel();
              modifiableModuleModel.renameModule(module, module.getProject().getName());
              modifiableModuleModel.commit();
            }
            catch (ConfigurationException e) {
              LOG.error(e);
            }
            catch (ModuleWithNameAlreadyExists exists) {
              // do nothing
            }
          }
        });
      }
      return null;
    }
    else {
      return super.commitModule(project, model);
    }
  }

  @Override
  public ModuleType getModuleType() {
    return myType;
  }

  @NotNull
  @Override
  public Module createModule(@NotNull ModifiableModuleModel moduleModel)
    throws InvalidDataException, IOException, ModuleWithNameAlreadyExists, JDOMException, ConfigurationException {
    final String path = getContentEntryPath();
    unzip(path, true);
    Module module = ImportImlMode.setUpLoader(getModuleFilePath()).createModule(moduleModel);
    if (myProjectMode) {
      moduleModel.renameModule(module, module.getProject().getName());
    }
    return module;
  }

  private void unzip(String path, boolean moduleMode) {
    File dir = new File(path);
    ZipInputStream zipInputStream = null;
    try {
      zipInputStream = myTemplate.getStream();
      ZipUtil.unzip(ProgressManager.getInstance().getProgressIndicator(), dir, zipInputStream, moduleMode ? PATH_CONVERTOR : null);
      String iml = ContainerUtil.find(dir.list(), new Condition<String>() {
        @Override
        public boolean value(String s) {
          return s.endsWith(".iml");
        }
      });
      if (moduleMode) {
        File from = new File(path, iml);
        File to = new File(getModuleFilePath());
        if (!from.renameTo(to)) {
          throw new IOException("Can't rename " + from + " to " + to);
        }
      }
      VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir);
      if (virtualFile == null) {
        throw new IOException("Can't find " + dir);
      }
      RefreshQueue.getInstance().refresh(false, true, null, virtualFile);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    finally {
      StreamUtil.closeStream(zipInputStream);
    }
  }

  @Nullable
  @Override
  public Project createProject(String name, final String path) {
    myProjectMode = true;
    unzip(path, false);
    return ApplicationManager.getApplication().runWriteAction(new NullableComputable<Project>() {
      @Nullable
      @Override
      public Project compute() {
        try {
          return ProjectManagerEx.getInstanceEx().convertAndLoadProject(path);
        }
        catch (IOException e) {
          LOG.error(e);
          return null;
        }
      }
    });
  }

  private final static Logger LOG = Logger.getInstance(TemplateModuleBuilder.class);
}
