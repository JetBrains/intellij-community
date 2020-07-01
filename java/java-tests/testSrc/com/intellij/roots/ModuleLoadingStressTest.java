// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.roots;

import com.intellij.configurationStore.StoreUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.ProjectKt;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ModuleLoadingStressTest extends HeavyPlatformTestCase {
  public void testContentEntryExchange() throws IOException {
    Path path = ProjectKt.getStateStore(myProject).getProjectBasePath();
    int count = 100;
    for (int i = 0; i < count; i++) {
      String name = "module" + i;
      Path folder = path.resolve(name);
      createModule(name, folder, path);
      String inner = "inner" + i;
      createModule(inner, folder.resolve(inner), path);
    }

    StoreUtil.saveSettings(myProject, false);

    String projectFilePath = myProject.getProjectFilePath();
    String moduleName = myModule.getName();
    PlatformTestUtil.forceCloseProjectWithoutSaving(myProject);

    myProject = PlatformTestUtil.loadAndOpenProject(Paths.get(projectFilePath));
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    assertEquals(count * 2 + 1, modules.length);

    for (Module module : modules) {
      if (moduleName.equals(module.getName())) continue;
      VirtualFile root = ModuleRootManager.getInstance(module).getContentRoots()[0];
      assertEquals(module.getName(), root.getName());
    }
  }

  private void createModule(String name, @NotNull Path contentRoot, @NotNull Path path) throws IOException {
    Module module = createModuleAt(name, myProject, JavaModuleType.getModuleType(), path);
    Files.createDirectories(contentRoot);
    VirtualFile root = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(contentRoot);
    ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
    model.addContentEntry(root);
    ApplicationManager.getApplication().runWriteAction(() -> model.commit());
  }
}
