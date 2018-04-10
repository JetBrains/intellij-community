// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.roots;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import kotlin.Unit;

import java.io.File;

/**
 * @author Dmitry Avdeev
 */
public class ModuleLoadingStressTest extends PlatformTestCase {
  public void testContentEntryExchange() throws Exception {
    String path = myProject.getBasePath();
    int count = 100;
    for (int i = 0; i < count; i++) {
      String name = "module" + i;
      File folder = new File(path, name);
      createModule(name, folder, path);
      String inner = "inner" + i;
      createModule(inner, new File(folder, inner), path);
    }

    com.intellij.application.UtilKt.runInAllowSaveMode(() -> {
      myProject.save();
      return Unit.INSTANCE;
    });

    String projectFilePath = myProject.getProjectFilePath();
    String moduleName = myModule.getName();
    ProjectManager manager = ProjectManager.getInstance();
    manager.closeProject(myProject);

    ApplicationManager.getApplication().runWriteAction(() -> Disposer.dispose(myProject));

    myProject = manager.loadAndOpenProject(projectFilePath);
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    assertEquals(count * 2 + 1, modules.length);

    for (Module module : modules) {
      if (moduleName.equals(module.getName())) continue;
      VirtualFile root = ModuleRootManager.getInstance(module).getContentRoots()[0];
      assertEquals(module.getName(), root.getName());
    }
  }

  private void createModule(String name, File contentRoot, String path) {
    Module module = createModuleAt(name, myProject, JavaModuleType.getModuleType(), path);
    assertTrue(contentRoot.mkdir());
    VirtualFile root = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(contentRoot);
    ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
    model.addContentEntry(root);
    ApplicationManager.getApplication().runWriteAction(() -> model.commit());
  }
}
