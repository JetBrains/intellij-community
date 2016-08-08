/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.platform;

import com.intellij.CommonBundle;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectAttachProcessor;
import com.intellij.projectImport.ProjectOpenedCallback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * @author yole
 */
public class ModuleAttachProcessor extends ProjectAttachProcessor {
  private static final Logger LOG = Logger.getInstance(ModuleAttachProcessor.class);

  @Override
  public boolean attachToProject(Project project, File projectDir, @Nullable ProjectOpenedCallback callback) {
    if (!projectDir.exists()) {
      final Project newProject = ((ProjectManagerEx)ProjectManager.getInstance())
        .newProject(projectDir.getParentFile().getName(), projectDir.getParent(), true, false);
      if (newProject == null) {
        return false;
      }
      final VirtualFile baseDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(projectDir.getParent());
      PlatformProjectOpenProcessor.runDirectoryProjectConfigurators(baseDir, newProject);
      newProject.save();
      ApplicationManager.getApplication().runWriteAction(() -> Disposer.dispose(newProject));
    }
    final String[] files = projectDir.list();
    if (files != null) {
      for (String file : files) {
        if (FileUtilRt.extensionEquals(file, "iml")) {
          VirtualFile imlFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(projectDir, file));
          if (imlFile != null) {
            attachModule(project, imlFile, callback);
            return true;
          }
        }
      }
    }
    int rc = Messages.showYesNoDialog(project, "The project at " +
                                               FileUtil.toSystemDependentName(projectDir.getPath()) +
                                               " uses a non-standard layout and cannot be attached to this project. Would you like to open it in a new window?",
                                      "Open Project", Messages.getQuestionIcon());

    return rc != Messages.YES;
  }

  private static void attachModule(Project project, VirtualFile imlFile, @Nullable ProjectOpenedCallback callback) {
    try {
      final ModifiableModuleModel model = ModuleManager.getInstance(project).getModifiableModel();
      final Module module = model.loadModule(imlFile.getPath());

      AccessToken token = WriteAction.start();
      try {
        model.commit();
      }
      finally {
        token.finish();
      }
      final Module newModule = ModuleManager.getInstance(project).findModuleByName(module.getName());
      assert newModule != null;
      final Module primaryModule = addPrimaryModuleDependency(project, newModule);
      if (primaryModule != null) {
        VirtualFile dotIdeaDir = imlFile.getParent();
        if (dotIdeaDir != null) {
          updateVcsMapping(primaryModule, dotIdeaDir.getParent());
        }
      }

      if (callback != null) {
        callback.projectOpened(project, newModule);
      }
    }
    catch (Exception ex) {
      LOG.info(ex);
      Messages.showErrorDialog(project, "Cannot attach project: " + ex.getMessage(), CommonBundle.getErrorTitle());
    }
  }

  private static void updateVcsMapping(Module primaryModule, VirtualFile addedModuleContentRoot) {
    final Project project = primaryModule.getProject();
    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    final List<VcsDirectoryMapping> mappings = vcsManager.getDirectoryMappings();
    if (mappings.size() == 1) {
      final VirtualFile[] contentRoots = ModuleRootManager.getInstance(primaryModule).getContentRoots();
      // if we had one mapping for the root of the primary module and the added module uses the same VCS, change mapping to <Project Root>
      if (contentRoots.length == 1 && FileUtil.filesEqual(new File(contentRoots[0].getPath()), new File(mappings.get(0).getDirectory()))) {
        final AbstractVcs vcs = vcsManager.findVersioningVcs(addedModuleContentRoot);
        if (vcs != null && vcs.getName().equals(mappings.get(0).getVcs())) {
          vcsManager.setDirectoryMappings(Arrays.asList(new VcsDirectoryMapping("", vcs.getName())));
          return;
        }
      }
    }
    final AbstractVcs vcs = vcsManager.findVersioningVcs(addedModuleContentRoot);
    if (vcs != null) {
      ArrayList<VcsDirectoryMapping> newMappings = new ArrayList<>(mappings);
      newMappings.add(new VcsDirectoryMapping(addedModuleContentRoot.getPath(), vcs.getName()));
      vcsManager.setDirectoryMappings(newMappings);
    }
  }

  @Nullable
  private static Module addPrimaryModuleDependency(Project project, @NotNull Module newModule) {
    final Module module = getPrimaryModule(project);
    if (module != null && module != newModule) {
      ModuleRootModificationUtil.addDependency(module, newModule);
      return module;
    }
    return null;
  }

  @Nullable
  public static Module getPrimaryModule(Project project) {
    if (!canAttachToProject()) {
      return null;
    }
    return findModuleInBaseDir(project);
  }

  @Nullable
  public static Module findModuleInBaseDir(Project project) {
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      final VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
      for (VirtualFile root : roots) {
        if (Comparing.equal(root, project.getBaseDir())) {
          return module;
        }
      }
    }
    return null;
  }

  public static List<Module> getSortedModules(Project project) {
    List<Module> result = new ArrayList<>();
    final Module primaryModule = getPrimaryModule(project);
    final Module[] modules = ModuleManager.getInstance(project).getModules();
    for (Module module : modules) {
      if (module != primaryModule) {
        result.add(module);
      }
    }
    Collections.sort(result, (module, module2) -> module.getName().compareTo(module2.getName()));
    if (primaryModule != null) {
      result.add(0, primaryModule);
    }
    return result;
  }

  /**
   * @param project the project
   * @return null if either multi-projects are not enabled or the project has only one module
   */
  @Nullable
  public static String getMultiProjectDisplayName(@NotNull Project project) {
    if (!ProjectAttachProcessor.canAttachToProject()) {
      return null;
    }

    final Module[] modules = ModuleManager.getInstance(project).getModules();
    if (modules.length <= 1) {
      return null;
    }

    Module primaryModule = getPrimaryModule(project);
    if (primaryModule == null) {
      primaryModule = modules [0];
    }
    final StringBuilder result = new StringBuilder(primaryModule.getName());
    result.append(", ");
    for (Module module : modules) {
      if (module == primaryModule) continue;
      result.append(module.getName());
      break;
    }
    if (modules.length > 2) {
      result.append("...");
    }
    return result.toString();
  }

}
