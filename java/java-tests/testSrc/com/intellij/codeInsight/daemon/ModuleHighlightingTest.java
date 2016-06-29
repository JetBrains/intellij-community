/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class ModuleHighlightingTest extends LightCodeInsightFixtureTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_9;
  }

  public void testWrongFileName() {
    myFixture.configureByText("M.java", "/* ... */ <error descr=\"Module declaration should be in a file named 'module-info.java'\">module M</error> { }");
    myFixture.checkHighlighting();
  }

  public void testFileDuplicate() throws IOException {
    myFixture.configureFromExistingVirtualFile(WriteAction.compute(() -> {
      VirtualFile file = LightPlatformTestCase.getSourceRoot().createChildDirectory(this, "pkg").createChildData(this, "module-info.java");
      VfsUtil.saveText(file, "module M { }");
      return file;
    }));
    myFixture.configureByText("module-info.java", "<error descr=\"Multiple module declarations\">module M</error> { }");
    myFixture.checkHighlighting();
  }

  public void testWrongFileLocation() throws IOException {
    myFixture.configureFromExistingVirtualFile(WriteAction.compute(() -> {
      VirtualFile file = LightPlatformTestCase.getSourceRoot().createChildDirectory(this, "pkg").createChildData(this, "module-info.java");
      VfsUtil.saveText(file, "<warning descr=\"Module declaration should be located in a module's source root\">module M</warning> { }");
      return file;
    }));
    myFixture.checkHighlighting();
  }
}