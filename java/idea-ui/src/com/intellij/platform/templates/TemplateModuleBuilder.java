/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.ide.util.newProjectWizard.modes.ImportImlMode;
import com.intellij.ide.util.projectWizard.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.*;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.platform.templates.github.ZipUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipInputStream;

/**
* @author Dmitry Avdeev
*         Date: 10/19/12
*/
public class TemplateModuleBuilder extends ModuleBuilder {

  private final ModuleType myType;
  private List<WizardInputField> myAdditionalFields;
  private ArchivedProjectTemplate myTemplate;
  private boolean myProjectMode;

  public TemplateModuleBuilder(ArchivedProjectTemplate template, ModuleType moduleType, List<WizardInputField> additionalFields) {
    myTemplate = template;
    myType = moduleType;
    myAdditionalFields = additionalFields;
  }

  @Override
  public void setupRootModel(ModifiableRootModel modifiableRootModel) throws ConfigurationException {

  }

  @Override
  public ModuleWizardStep[] createWizardSteps(@NotNull WizardContext wizardContext, @NotNull ModulesProvider modulesProvider) {
    ModuleBuilder builder = myType.createModuleBuilder();
    return builder.createWizardSteps(wizardContext, modulesProvider);
  }

  @Override
  protected List<WizardInputField> getAdditionalFields() {
    return myAdditionalFields;
  }

  @Override
  public Module commitModule(@NotNull final Project project, ModifiableModuleModel model) {
    if (myProjectMode) {
      final Module[] modules = ModuleManager.getInstance(project).getModules();
      if (modules.length > 0) {
        final Module module = modules[0];
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            try {
              setupModule(module);
            }
            catch (ConfigurationException e) {
              LOG.error(e);
            }
          }
        });

        StartupManager.getInstance(project).registerPostStartupActivity(new Runnable() {
          @Override
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              @Override
              public void run() {
                try {
                  ModifiableModuleModel modifiableModuleModel = ModuleManager.getInstance(project).getModifiableModel();
                  modifiableModuleModel.renameModule(module, module.getProject().getName());
                  modifiableModuleModel.commit();
                  fixModuleName(module);
                }
                catch (ModuleWithNameAlreadyExists exists) {
                  // do nothing
                }
              }
            });
          }
        });
        return module;
      }
      return null;
    }
    else {
      return super.commitModule(project, model);
    }
  }

  @Nullable
  @Override
  public String getBuilderId() {
    return myTemplate.getName();
  }

  @Override
  public ModuleType getModuleType() {
    return myType;
  }

  @Override
  public Icon getNodeIcon() {
    return myTemplate.getIcon();
  }

  @Override
  public boolean isTemplateBased() {
    return true;
  }

  @NotNull
  @Override
  public Module createModule(@NotNull ModifiableModuleModel moduleModel)
    throws InvalidDataException, IOException, ModuleWithNameAlreadyExists, JDOMException, ConfigurationException {
    final String path = getContentEntryPath();
    final ExistingModuleLoader loader = ImportImlMode.setUpLoader(getModuleFilePath());
    unzip(loader.getName(), path, true);
    Module module = loader.createModule(moduleModel);
    if (myProjectMode) {
      moduleModel.renameModule(module, module.getProject().getName());
    }
    fixModuleName(module);
    return module;
  }

  private void fixModuleName(Module module) {
    List<RunConfiguration> configurations = RunManager.getInstance(module.getProject()).getAllConfigurationsList();
    for (RunConfiguration configuration : configurations) {
      if (configuration instanceof ModuleBasedConfiguration) {
        ((ModuleBasedConfiguration)configuration).getConfigurationModule().setModule(module);
      }
    }
    ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
    for (WizardInputField field : myAdditionalFields) {
      ProjectTemplateParameterFactory factory = WizardInputField.getFactoryById(field.getId());
      factory.applyResult(field.getValue(), model);
    }
    model.commit();
  }

  private WizardInputField getBasePackageField() {
    for (WizardInputField field : getAdditionalFields()) {
      if (ProjectTemplateParameterFactory.IJ_BASE_PACKAGE.equals(field.getId())) {
        return field;
      }
    }
    return null;
  }

  private void unzip(final @Nullable String projectName, String path, final boolean moduleMode) {
    final WizardInputField basePackage = getBasePackageField();
    try {
      final NullableFunction<String, String> pathConvertor = new NullableFunction<String, String>() {
        @Nullable
        @Override
        public String fun(String path) {
          if (moduleMode && path.contains(Project.DIRECTORY_STORE_FOLDER)) return null;
          if (basePackage != null) {
            return path.replace(getPathFragment(basePackage.getDefaultValue()), getPathFragment(basePackage.getValue()));
          }
          return path;
        }
      };

      final File dir = new File(path);
      myTemplate.processStream(new ArchivedProjectTemplate.StreamProcessor<Void>() {
        @Override
        public Void consume(@NotNull ZipInputStream stream) throws IOException {
          ZipUtil.unzip(ProgressManager.getInstance().getProgressIndicator(), dir, stream, pathConvertor, new ZipUtil.ContentProcessor() {
            @Override
            public byte[] processContent(byte[] content, File file) throws IOException {
              FileType fileType = FileTypeManager.getInstance().getFileTypeByExtension(FileUtilRt.getExtension(file.getName()));
              return fileType.isBinary() ? content : processTemplates(projectName, new String(content, CharsetToolkit.UTF8_CHARSET), file);
            }
          }, true);
          return null;
        }
      });

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
  }

  private static String getPathFragment(String value) {
    return "/" + value.replace('.', '/') + "/";
  }

  @SuppressWarnings("UseOfPropertiesAsHashtable")
  @Nullable
  private byte[] processTemplates(@Nullable String projectName, String content, File file) throws IOException {
    for (WizardInputField field : myAdditionalFields) {
      if (!field.acceptFile(file)) {
        return null;
      }
    }
    Properties properties = FileTemplateManager.getDefaultInstance().getDefaultProperties();
    for (WizardInputField field : myAdditionalFields) {
      properties.putAll(field.getValues());
    }
    if (projectName != null) {
      properties.put(ProjectTemplateParameterFactory.IJ_PROJECT_NAME, projectName);
    }
    String merged = FileTemplateUtil.mergeTemplate(properties, content, true);
    return StringUtilRt.convertLineSeparators(merged.replace("\\$", "$").replace("\\#", "#"), SystemInfo.isWindows ? "\r\n" : "\n").getBytes(
      CharsetToolkit.UTF8_CHARSET);
  }

  @Nullable
  @Override
  public Project createProject(String name, final String path) {
    myProjectMode = true;
    unzip(name, path, false);
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
