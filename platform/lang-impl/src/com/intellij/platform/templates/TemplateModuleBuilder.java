/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.codeStyle.CodeStyleFacade;
import com.intellij.diagnostic.LogMessageEx;
import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.ide.util.projectWizard.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.*;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.platform.templates.github.ZipUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.apache.velocity.exception.VelocityException;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipInputStream;

/**
* @author Dmitry Avdeev
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
        ApplicationManager.getApplication().runWriteAction(() -> {
          try {
            setupModule(module);
          }
          catch (ConfigurationException e) {
            LOG.error(e);
          }
        });

        StartupManager.getInstance(project).registerPostStartupActivity(() -> ApplicationManager.getApplication().runWriteAction(() -> {
          try {
            ModifiableModuleModel modifiableModuleModel = ModuleManager.getInstance(project).getModifiableModel();
            modifiableModuleModel.renameModule(module, module.getProject().getName());
            modifiableModuleModel.commit();
            fixModuleName(module);
          }
          catch (ModuleWithNameAlreadyExists exists) {
            // do nothing
          }
        }));
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
    final ExistingModuleLoader loader = ExistingModuleLoader.setUpLoader(getModuleFilePath());
    unzip(loader.getName(), path, true, null, true);
    Module module = loader.createModule(moduleModel);
    if (myProjectMode) {
      moduleModel.renameModule(module, module.getProject().getName());
    }
    fixModuleName(module);
    return module;
  }

  private void fixModuleName(Module module) {
    for (RunConfiguration configuration : RunManager.getInstance(module.getProject()).getAllConfigurationsList()) {
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

  private void unzip(final @Nullable String projectName,
                     String path,
                     final boolean moduleMode,
                     @Nullable ProgressIndicator pI,
                     boolean reportFailuresWithDialog) {
    final WizardInputField basePackage = getBasePackageField();
    try {
      final File dir = new File(path);
      class ExceptionConsumer implements Consumer<VelocityException> {
        private String myPath;
        private String myText;
        private SmartList<Trinity<String, String, VelocityException>> myFailures = new SmartList<>();

        @Override
        public void consume(VelocityException e) {
          myFailures.add(Trinity.create(myPath, myText, e));
        }

        private void setCurrentFile(String path, String text) {
          myPath = path;
          myText = text;
        }

        private void reportFailures() {
          if (myFailures.isEmpty()) {
            return;
          }

          if(reportFailuresWithDialog) {
            String dialogMessage;
            if (myFailures.size() == 1) {
              dialogMessage = "Failed to decode file \'" + myFailures.get(0).getFirst() + "\'";
            }
            else {
              StringBuilder dialogMessageBuilder = new StringBuilder();
              dialogMessageBuilder.append("Failed to decode files: \n");
              for (Trinity<String, String, VelocityException> failure : myFailures) {
                dialogMessageBuilder.append(failure.getFirst()).append("\n");
              }
              dialogMessage = dialogMessageBuilder.toString();
            }
            Messages.showErrorDialog(dialogMessage, "Decoding Template");
          }

          StringBuilder reportBuilder = new StringBuilder();
          for (Trinity<String, String, VelocityException> failure : myFailures) {
            reportBuilder.append("File: ").append(failure.getFirst()).append("\n");
            reportBuilder.append("Exception:\n").append(ExceptionUtil.getThrowableText(failure.getThird())).append("\n");
            reportBuilder.append("File content:\n\'").append(failure.getSecond()).append("\'\n");
            reportBuilder.append("\n===========================================\n");
          }

          LOG.error(LogMessageEx.createEvent("Cannot decode files in template", "",
                                             new Attachment("Files in template", reportBuilder.toString())));
        }
      }
      ExceptionConsumer consumer = new ExceptionConsumer();

      List<File> filesToRefresh = new ArrayList<>();
      myTemplate.processStream(new ArchivedProjectTemplate.StreamProcessor<Void>() {
        @Override
        public Void consume(@NotNull ZipInputStream stream) throws IOException {
          ZipUtil.unzip(ProgressManager.getInstance().getProgressIndicator(), dir, stream, path1 -> {
            if (moduleMode && path1.contains(Project.DIRECTORY_STORE_FOLDER)) {
              return null;
            }
            if (basePackage != null) {
              return path1.replace(getPathFragment(basePackage.getDefaultValue()), getPathFragment(basePackage.getValue()));
            }
            return path1;
          }, new ZipUtil.ContentProcessor() {
            @Override
            public byte[] processContent(byte[] content, File file) throws IOException {
              if(pI != null){
                pI.checkCanceled();
              }
              FileType fileType = FileTypeManager.getInstance().getFileTypeByExtension(FileUtilRt.getExtension(file.getName()));
              String text = new String(content, CharsetToolkit.UTF8_CHARSET);
              consumer.setCurrentFile(file.getName(), text);
              return fileType.isBinary() ? content : processTemplates(projectName, text, file, consumer);
            }
          }, true);

          myTemplate.handleUnzippedDirectories(dir, filesToRefresh);

          return null;
        }
      });

      if(pI != null) {
        pI.setText("Refreshing...");
      }

      String iml = ContainerUtil.find(dir.list(), s -> s.endsWith(".iml"));
      if (moduleMode) {
        File from = new File(path, iml);
        File to = new File(getModuleFilePath());
        if (!from.renameTo(to)) {
          throw new IOException("Can't rename " + from + " to " + to);
        }
      }

      RefreshQueue refreshQueue = RefreshQueue.getInstance();
      LOG.assertTrue(!filesToRefresh.isEmpty());
      for (File file : filesToRefresh) {
        VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
        if (virtualFile == null) {
          throw new IOException("Can't find " + file);
        }
        refreshQueue.refresh(false, true, null, virtualFile);
      }

      consumer.reportFailures();
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
  private byte[] processTemplates(@Nullable String projectName, String content, File file, Consumer<VelocityException> exceptionConsumer)
    throws IOException {
    String patchedContent = content;
    if (!(myTemplate instanceof LocalArchivedTemplate) || ((LocalArchivedTemplate)myTemplate).isEscaped()) {
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
      String merged = FileTemplateUtil.mergeTemplate(properties, content, true, exceptionConsumer);
      patchedContent = merged.replace("\\$", "$").replace("\\#", "#");
    }
    else {
      int i = content.indexOf(SaveProjectAsTemplateAction.FILE_HEADER_TEMPLATE_PLACEHOLDER);
      if (i != -1) {
        final FileTemplate template =
          FileTemplateManager.getDefaultInstance().getDefaultTemplate(SaveProjectAsTemplateAction.getFileHeaderTemplateName());
        Properties properties = FileTemplateManager.getDefaultInstance().getDefaultProperties();
        String templateText = template.getText(properties);
        patchedContent = patchedContent.substring(0, i) +
                         templateText +
                         patchedContent.substring(i + SaveProjectAsTemplateAction.FILE_HEADER_TEMPLATE_PLACEHOLDER.length());
      }
    }
    return StringUtilRt.convertLineSeparators(patchedContent, CodeStyleFacade.getInstance().getLineSeparator()).
      getBytes(CharsetToolkit.UTF8_CHARSET);
  }

  @Nullable
  @Override
  public Project createProject(String name, final String path) {
    final File location = new File(FileUtil.toSystemDependentName(path));
    LOG.assertTrue(location.exists());

    final VirtualFile baseDir = WriteAction.compute(() -> LocalFileSystem.getInstance().refreshAndFindFileByIoFile(location));
    if (baseDir == null) {
      LOG.error("Couldn't find path '" + path + "' in VFS");
      return null;
    }

    VirtualFile[] children = baseDir.getChildren();
    boolean isSomehowOverwriting = children.length > 1 ||
                                   (children.length == 1 && !PathMacroUtil.DIRECTORY_STORE_NAME.equals(children[0].getName()));

    Task.WithResult<Project, RuntimeException> task = new Task.WithResult<Project, RuntimeException>(null, "Applying Template", true) {
      @Override
      public Project compute(@NotNull ProgressIndicator indicator) {
        try {
          myProjectMode = true;
          unzip(name, path, false, indicator, false);
          return ProjectManagerEx.getInstanceEx().convertAndLoadProject(path);
        }
        catch (IOException e) {
          LOG.error(e);
          return null;
        }
        finally {
          cleanup();
          if(indicator.isCanceled()){
            if (!isSomehowOverwriting) {
              ApplicationManager.getApplication().invokeLater(() -> {
                try {
                  WriteAction.run(() -> baseDir.delete(TemplateProjectDirectoryGenerator.class));
                }
                catch (IOException e) {
                  LOG.error(e);
                }
              });
            }
          }
        }
      }
    };
    return ProgressManager.getInstance().run(task);
  }

  private final static Logger LOG = Logger.getInstance(TemplateModuleBuilder.class);
}
