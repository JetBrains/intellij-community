// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard;

import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author Dmitry Avdeev
 */
public class ModuleWizardTest extends NewProjectWizardTestCase {
  public void testPlainJava() throws Exception {
    Module module = createModuleFromTemplate(JavaModuleType.JAVA_GROUP, null, null);
    assertNotNull(module);
    VirtualFile root = ModuleRootManager.getInstance(module).getContentRoots()[0];
    assertEquals(1, root.getChildren().length);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    createSdk("foo", JavaSdk.getInstance());
  }
}
