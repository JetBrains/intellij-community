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
package com.intellij.ide.util.projectWizard;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.*;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EventDispatcher;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class ModuleBuilder extends ProjectBuilder{
  private static final ExtensionPointName<ModuleBuilderFactory> EP_NAME = ExtensionPointName.create("com.intellij.moduleBuilder");

  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.projectWizard.ModuleBuilder");
  private String myName;
  @NonNls private String myModuleFilePath;
  private String myContentEntryPath;
  private final List<ModuleConfigurationUpdater> myUpdaters = new ArrayList<ModuleConfigurationUpdater>();
  private final EventDispatcher<ModuleBuilderListener> myDispatcher = EventDispatcher.create(ModuleBuilderListener.class);

  public static List<ModuleBuilder> getAllBuilders() {
    final ArrayList<ModuleBuilder> result = new ArrayList<ModuleBuilder>();
    for (final ModuleType moduleType : ModuleTypeManager.getInstance().getRegisteredTypes()) {
      result.add(moduleType.createModuleBuilder());
    }
    for (ModuleBuilderFactory factory : EP_NAME.getExtensions()) {
      result.add(factory.createBuilder());
    }
    return result;
  }

  @Nullable
  protected final String acceptParameter(String param) {
    return param != null && param.length() > 0 ? param : null;
  }

  public String getName() {
    return myName;
  }

  public String getBuilderId() {
    return getModuleType().getId();
  }

  public ModuleWizardStep[] createWizardSteps(WizardContext wizardContext, ModulesProvider modulesProvider) {
    return getModuleType().createWizardSteps(wizardContext, this, modulesProvider);
  }

  public void setName(String name) {
    myName = acceptParameter(name);
  }

  public String getModuleFilePath() {
    return myModuleFilePath;
  }

  public void addModuleConfigurationUpdater(ModuleConfigurationUpdater updater) {
    myUpdaters.add(updater);
  }

  public void setModuleFilePath(@NonNls String path) {
    myModuleFilePath = acceptParameter(path);
  }

  @Nullable
  public String getContentEntryPath() {
    if (myContentEntryPath == null) {
      final String directory = getModuleFileDirectory();
      if (directory == null) {
        return null;
      }
      new File(directory).mkdirs();
      return directory;
    }
    return myContentEntryPath;
  }

  public void setContentEntryPath(String moduleRootPath) {
    final String path = acceptParameter(moduleRootPath);
    if (path != null) {
      try {
        myContentEntryPath = FileUtil.resolveShortWindowsName(path);
      }
      catch (IOException e) {
        myContentEntryPath = path;
      }
    }
    else {
      myContentEntryPath = null;
    }
    if (myContentEntryPath != null) {
      myContentEntryPath = myContentEntryPath.replace(File.separatorChar, '/');
    }
  }

  protected @Nullable ContentEntry doAddContentEntry(ModifiableRootModel modifiableRootModel) {
    final String contentEntryPath = getContentEntryPath();
    if (contentEntryPath == null) return null;
    final VirtualFile moduleContentRoot = LocalFileSystem.getInstance().refreshAndFindFileByPath(contentEntryPath.replace('\\', '/'));
    if (moduleContentRoot == null) return null;
    return modifiableRootModel.addContentEntry(moduleContentRoot);
  }

  @Nullable
  public String getModuleFileDirectory() {
    if (myModuleFilePath == null) {
      return null;
    }
    final String parent = new File(myModuleFilePath).getParent();
    if (parent == null) {
      return null;
    }
    return parent.replace(File.separatorChar, '/');
  }

  @NotNull
  public Module createModule(ModifiableModuleModel moduleModel)
    throws InvalidDataException, IOException, ModuleWithNameAlreadyExists, JDOMException, ConfigurationException {
    LOG.assertTrue(myName != null);
    LOG.assertTrue(myModuleFilePath != null);

    deleteModuleFile(myModuleFilePath);
    final ModuleType moduleType = getModuleType();
    final Module module = moduleModel.newModule(myModuleFilePath, moduleType);
    final ModifiableRootModel modifiableModel = ModuleRootManager.getInstance(module).getModifiableModel();
    setupRootModel(modifiableModel);
    for (ModuleConfigurationUpdater updater : myUpdaters) {
      updater.update(module, modifiableModel);
    }
    modifiableModel.commit();

    return module;
  }

  private void onModuleInitialized(final Module module) {
    myDispatcher.getMulticaster().moduleCreated(module);
  }

  public abstract void setupRootModel(ModifiableRootModel modifiableRootModel) throws ConfigurationException;

  public abstract ModuleType getModuleType();

  @NotNull
  public Module createAndCommitIfNeeded(final Project project, ModifiableModuleModel model, boolean runFromProjectWizard) throws
                                                                                                 InvalidDataException,
                                                                                                 ConfigurationException,
                                                                                                 IOException,
                                                                                                 JDOMException,
                                                                                                 ModuleWithNameAlreadyExists{
    final ModifiableModuleModel moduleModel = model != null ? model : ModuleManager.getInstance(project).getModifiableModel();
    final Module module = createModule(moduleModel);
    if (model == null) moduleModel.commit();

    if (runFromProjectWizard) {
      StartupManager.getInstance(module.getProject()).runWhenProjectIsInitialized(new DumbAwareRunnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            onModuleInitialized(module);
          }
        });
      }
    });
    }
    else {
      onModuleInitialized(module);
    }
    return module;
  }


  public void addListener(ModuleBuilderListener listener) {
    myDispatcher.addListener(listener);
  }

  public void removeListener(ModuleBuilderListener listener) {
    myDispatcher.removeListener(listener);
  }

  public boolean canCreateModule() {
    return true;
  }

  @Nullable
  public List<Module> commit(final Project project, final ModifiableModuleModel model, final ModulesProvider modulesProvider) {
    final Module module = commitModule(project, model);
    return module != null ? Collections.singletonList(module) : null;
  }

  public Module commitModule(final Project project, final ModifiableModuleModel model) {
    final Ref<Module> result = new Ref<Module>();
    if (canCreateModule()) {
      if (myName == null) {
        myName = project.getName();
      }
      if (myModuleFilePath == null) {
        myModuleFilePath = project.getBaseDir().getPath() + File.separator + myName + ModuleFileType.DOT_DEFAULT_EXTENSION;
      }
      Exception ex = ApplicationManager.getApplication().runWriteAction(new Computable<Exception>() {
        public Exception compute() {
          try {
            result.set(createAndCommitIfNeeded(project, model, true));
            return null;
          }
          catch (Exception e) {
            return e;
          }
        }
      });
      if (ex != null) {
        LOG.info(ex);
        Messages.showErrorDialog(IdeBundle.message("error.adding.module.to.project", ex.getMessage()), IdeBundle.message("title.add.module"));
      }
    }
    return result.get();
  }

  public static void deleteModuleFile(String moduleFilePath) {
    final File moduleFile = new File(moduleFilePath);
    if (moduleFile.exists()) {
      FileUtil.delete(moduleFile);
    }
    final VirtualFile file = LocalFileSystem.getInstance().findFileByIoFile(moduleFile);
    if (file != null) {
      file.refresh(false, false);
    }
  }

  public Icon getBigIcon() {
    return getModuleType().getBigIcon();
  }

  public String getDescription() {
    return getModuleType().getDescription();
  }

  public String getPresentableName() {
    return getModuleType().getName();
  }

  public static abstract class ModuleConfigurationUpdater {

    public abstract void update(@NotNull Module module, @NotNull ModifiableRootModel rootModel);

  }
}
