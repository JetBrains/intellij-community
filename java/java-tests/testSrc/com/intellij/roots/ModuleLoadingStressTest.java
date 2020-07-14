// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.roots;

import com.intellij.configurationStore.StoreUtil;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.project.ProjectKt;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class ModuleLoadingStressTest extends HeavyPlatformTestCase {
  public void testContentEntryExchange() {
    Path path = ProjectKt.getStateStore(getProject()).getProjectBasePath();
    int count = 100;
    for (int i = 0; i < count; i++) {
      String name = "module" + i;
      Path dir = path.resolve(name);
      createModule(name, dir, path, getProject());
      String inner = "inner" + i;
      createModule(inner, dir.resolve(inner), path, getProject());
    }

    StoreUtil.saveSettings(myProject, false);

    String projectFilePath = myProject.getProjectFilePath();
    String moduleName = myModule.getName();
    PlatformTestUtil.forceCloseProjectWithoutSaving(myProject);

    myProject = PlatformTestUtil.loadAndOpenProject(Paths.get(projectFilePath));
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    assertThat(modules).hasSize(count * 2 + 1);

    for (Module module : modules) {
      if (moduleName.equals(module.getName())) {
        continue;
      }

      String root = ModuleRootManager.getInstance(module).getContentRootUrls()[0];
      assertThat(PathUtil.getFileName(VfsUtilCore.urlToPath(root))).isEqualTo(module.getName());
    }
  }

  private static void createModule(@NotNull String name, @NotNull Path contentRoot, @NotNull Path path, @NotNull Project project) {
    String moduleType = JavaModuleType.getModuleType().getId();
    Path moduleFile = path.resolve(name + ModuleFileType.DOT_DEFAULT_EXTENSION);
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    ApplicationManager.getApplication().runWriteAction(() -> {
      Module module = moduleManager.newModule(moduleFile, moduleType);
      ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
      model.addContentEntry(VfsUtilCore.pathToUrl(contentRoot.toString()));
      model.commit();
    });
  }
}
