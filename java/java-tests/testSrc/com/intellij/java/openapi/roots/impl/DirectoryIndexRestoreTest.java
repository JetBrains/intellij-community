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
package com.intellij.java.openapi.roots.impl;

import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.VfsTestUtil;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@PlatformTestCase.WrapInCommand
public class DirectoryIndexRestoreTest extends IdeaTestCase {
  private VirtualFile myTempVFile;
  private String myTestDirPath;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    LocalFileSystem fs = LocalFileSystem.getInstance();
    File temp = createTempDirectory();
    myTempVFile = fs.findFileByIoFile(temp);
    assertNotNull(myTempVFile);
    File root = new File(temp, "top/d1/d2/root");
    assertTrue(root.mkdirs());
    VirtualFile rootVFile = fs.findFileByIoFile(root);
    assertNotNull(rootVFile);

    VirtualFile moduleDir = createChildDirectory(rootVFile, "module");
    VirtualFile srcDir = createChildDirectory(moduleDir, "src");
    VirtualFile testDir = createChildDirectory(srcDir, "pkg");
    ModuleRootModificationUtil.setModuleSdk(myModule, null);
    PsiTestUtil.addContentRoot(myModule, moduleDir);
    PsiTestUtil.addSourceRoot(myModule, srcDir);

    myTestDirPath = testDir.getPath();

    // to not interfere with previous test firing vfs events
    VirtualFileManager.getInstance().syncRefresh();
  }

  public void testDeepDeleteAndRecreate() throws IOException {
    AtomicInteger counter = new AtomicInteger(0);
    ContentIterator iterator = file -> {
      boolean found = file.getPath().equals(myTestDirPath);
      if (found) counter.incrementAndGet();
      return !found;
    };
    File topFile = new File(myTempVFile.getPath(), "top");
    File bakFile = new File(myTempVFile.getPath(), "top.bak");
    String topPath = FileUtil.toSystemIndependentName(topFile.getPath());

    ProjectRootManager.getInstance(myProject).getFileIndex().iterateContent(iterator);
    assertEquals(1, counter.get());

    FileUtil.rename(topFile, bakFile);
    List<String> events1 = VfsTestUtil.print(VfsTestUtil.getEvents(() -> myTempVFile.refresh(false, true)));
    assertEquals(Collections.singletonList("D : " + topPath), events1);
    ProjectRootManager.getInstance(myProject).getFileIndex().iterateContent(iterator);
    assertEquals(1, counter.get());

    FileUtil.rename(bakFile, topFile);
    List<String> events2 = VfsTestUtil.print(VfsTestUtil.getEvents(() -> myTempVFile.refresh(false, true)));
    assertEquals(Collections.singletonList("C : " + topPath), events2);
    ProjectRootManager.getInstance(myProject).getFileIndex().iterateContent(iterator);
    assertEquals(2, counter.get());
  }
}