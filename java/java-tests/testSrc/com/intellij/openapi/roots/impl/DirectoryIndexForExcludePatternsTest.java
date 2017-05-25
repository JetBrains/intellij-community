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
package com.intellij.openapi.roots.impl;

import com.intellij.ide.projectView.actions.MarkRootActionBase;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Arrays;

/**
 * @author nik
 */
public class DirectoryIndexForExcludePatternsTest extends DirectoryIndexTestCase {
  private VirtualFile myContentRoot;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final File root = createTempDirectory();
    myContentRoot = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(root);
    ModuleRootModificationUtil.addContentRoot(myModule, myContentRoot.getPath());
  }

  public void testExcludeFileByExtension() {
    addExcludePattern("*.txt");
    VirtualFile dir = createChildDirectory(myContentRoot, "dir");
    VirtualFile txt1 = createChildData(myContentRoot, "a.txt");
    VirtualFile txt2 = createChildData(dir, "a.txt");
    VirtualFile java1 = createChildData(myContentRoot, "A.java");
    VirtualFile java2 = createChildData(dir, "A.java");
    assertExcluded(txt1, myModule);
    assertExcluded(txt2, myModule);
    assertNotExcluded(java1);
    assertNotExcluded(java2);
    assertIteratedContent(myModule, Arrays.asList(java1, java2), Arrays.asList(txt1, txt2));
  }

  public void testExcludeDirectoryByName() {
    addExcludePattern("exc");
    VirtualFile dir = createChildDirectory(myContentRoot, "dir");
    VirtualFile exc = createChildDirectory(myContentRoot, "exc");
    VirtualFile dirUnderExc = createChildDirectory(exc, "dir2");
    VirtualFile excUnderDir = createChildDirectory(dir, "exc");
    VirtualFile underExc = createChildData(exc, "a.txt");
    VirtualFile underDir = createChildData(dir, "a.txt");
    VirtualFile underExcUnderDir = createChildData(excUnderDir, "a.txt");
    VirtualFile underDirUnderExc = createChildData(dirUnderExc, "a.txt");
    assertExcluded(exc, myModule);
    assertExcluded(underExc, myModule);
    assertExcluded(dirUnderExc, myModule);
    assertExcluded(underDirUnderExc, myModule);
    assertExcluded(underExcUnderDir, myModule);
    assertNotExcluded(dir);
    assertNotExcluded(underDir);
    assertIteratedContent(myModule, Arrays.asList(underDir), Arrays.asList(underExc, underDirUnderExc, underExcUnderDir));
  }

  private void addExcludePattern(@NotNull String pattern) {
    ModuleRootModificationUtil.updateModel(myModule,
                                           model -> MarkRootActionBase.findContentEntry(model, myContentRoot).addExcludePattern(pattern));
  }
}
