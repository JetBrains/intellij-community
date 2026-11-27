// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.openapi.roots.impl;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.createChildDirectory;
import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class ProjectFileIndexRestoreTest {
  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture = heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath());

  private VirtualFile myTempVFile;
  private String myTestDirPath;

  @BeforeEach
  void setUp() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();

    VirtualFileSystem fs = StandardFileSystems.local();
    File temp = fixture.createTempVirtualDir().toNioPath().toFile();
    myTempVFile = fs.refreshAndFindFileByPath(temp.getAbsolutePath());
    assertNotNull(myTempVFile);
    File root = new File(temp, "top/d1/d2/root");
    assertTrue(root.mkdirs());
    VirtualFile rootVFile = fs.refreshAndFindFileByPath(root.getAbsolutePath());
    assertNotNull(rootVFile);

    VirtualFile moduleDir = createChildDirectory(rootVFile, "module");
    VirtualFile srcDir = createChildDirectory(moduleDir, "src");
    VirtualFile testDir = createChildDirectory(srcDir, "pkg");
    ModuleRootModificationUtil.setModuleSdk(fixture.getModule(), null);
    PsiTestUtil.addContentRoot(fixture.getModule(), moduleDir);
    PsiTestUtil.addSourceRoot(fixture.getModule(), srcDir);

    myTestDirPath = testDir.getPath();

    // to not interfere with previous test firing vfs events
    VfsTestUtil.syncRefresh();
  }

  @Test
  public void testDeepDeleteAndRecreate() throws IOException {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
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

    ReadAction.runBlocking(() -> ProjectRootManager.getInstance(fixture.getProject()).getFileIndex().iterateContent(iterator));
    assertEquals(1, counter.get());

    FileUtil.rename(topFile, bakFile);
    List<String> events1 = VfsTestUtil.print(VfsTestUtil.getEvents(() -> myTempVFile.refresh(false, true)));
    ReadAction.runBlocking(() -> ProjectRootManager.getInstance(fixture.getProject()).getFileIndex().iterateContent(iterator));
    assertEquals(1, counter.get(), "Events recorded: "+events1);

    FileUtil.rename(bakFile, topFile);
    List<String> events2 = VfsTestUtil.print(VfsTestUtil.getEvents(() -> myTempVFile.refresh(false, true)));
    ReadAction.runBlocking(() -> ProjectRootManager.getInstance(fixture.getProject()).getFileIndex().iterateContent(iterator));
    assertEquals(2, counter.get(), "Events recorded: "+events2);
  }
}