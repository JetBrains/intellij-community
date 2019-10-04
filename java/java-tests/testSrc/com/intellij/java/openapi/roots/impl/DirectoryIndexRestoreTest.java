// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.openapi.roots.impl;

import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.testFramework.*;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@HeavyPlatformTestCase.WrapInCommand
public class DirectoryIndexRestoreTest extends JavaProjectTestCase {
  private VirtualFile myTempVFile;
  private String myTestDirPath;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    LocalFileSystem fs = LocalFileSystem.getInstance();
    File temp = createTempDirectory();
    myTempVFile = fs.refreshAndFindFileByIoFile(temp);
    assertNotNull(myTempVFile);
    File root = new File(temp, "top/d1/d2/root");
    assertTrue(root.mkdirs());
    VirtualFile rootVFile = fs.refreshAndFindFileByIoFile(root);
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
    assertTrue(topFile.exists());
    File bakFile = new File(myTempVFile.getPath(), "top.bak");
    assertFalse(bakFile.exists());

    ProjectRootManager.getInstance(myProject).getFileIndex().iterateContent(iterator);
    assertEquals(1, counter.get());

    FileUtil.rename(topFile, bakFile);
    List<String> events1 = VfsTestUtil.print(VfsTestUtil.getEvents(() -> myTempVFile.refresh(false, true)));
    ProjectRootManager.getInstance(myProject).getFileIndex().iterateContent(iterator);
    assertEquals("Events recorded: "+events1, 1, counter.get());

    FileUtil.rename(bakFile, topFile);
    List<String> events2 = VfsTestUtil.print(VfsTestUtil.getEvents(() -> myTempVFile.refresh(false, true)));
    ProjectRootManager.getInstance(myProject).getFileIndex().iterateContent(iterator);
    assertEquals("Events recorded: "+events2, 2, counter.get());
  }
}