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
package com.intellij.roots;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;

import java.io.File;

/**
 * @author Dmitry Avdeev
 */
public class ModuleLoadingStressTest extends PlatformTestCase {

  public void testContentEntryExchange() throws Exception {
    String path = myProject.getBasePath();
    int count = 1000;
    for (int i = 0; i < count; i++) {
      String name = "module" + i;
      Module module = createModule(name);
      File folder = new File(path, name);
      assertTrue(folder.mkdir());
      VirtualFile root = LocalFileSystem.getInstance().findFileByIoFile(folder);

      ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
      model.addContentEntry(root);
      ApplicationManager.getApplication().runWriteAction(() -> model.commit());
    }

    ApplicationManagerEx.getApplicationEx().doNotSave(false);
    myProject.save();
    String projectFilePath = myProject.getProjectFilePath();
    String moduleName = myModule.getName();
    ProjectManager manager = ProjectManager.getInstance();
    manager.closeProject(myProject);
    ApplicationManager.getApplication().runWriteAction(() -> Disposer.dispose(myProject));

    myProject = manager.loadAndOpenProject(projectFilePath);
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    assertEquals(count + 1, modules.length);

    for (Module module: modules) {
      if (moduleName.equals(module.getName())) continue;
      VirtualFile root = ModuleRootManager.getInstance(module).getContentRoots()[0];
      assertEquals(module.getName(), root.getName());
    }
  }
}
