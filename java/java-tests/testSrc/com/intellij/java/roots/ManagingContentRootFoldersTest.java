// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.roots;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ContentFolder;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.createChildDirectory;
import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public class ManagingContentRootFoldersTest {
  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture = heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath());

  private VirtualFile root;
  private ContentEntry entry;
  private ModifiableRootModel myModel;

  @BeforeEach
  void setUp() {
    WriteAction.runAndWait(() -> {
      initContentRoot();
      initModifiableModel();
    });
  }

  @AfterEach
  void tearDown() {
    if (myModel != null && myModel.isWritable()) {
      myModel.dispose();
    }
  }

  private void initContentRoot() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    File dir = fixture.createTempVirtualDir().toNioPath().toFile();
    root = StandardFileSystems.local().refreshAndFindFileByPath(dir.getAbsolutePath());
    PsiTestUtil.addContentRoot(fixture.getModule(), root);
  }

  private void initModifiableModel() {
    myModel = ModuleRootManager.getInstance(heavyFixture.get().getModule()).getModifiableModel();
    for (ContentEntry e : myModel.getContentEntries()) {
      if (Comparing.equal(e.getFile(), root)) entry = e;
    }
  }

  @Test
  public void testCreationOfSourceFolderWithFile() {
    VirtualFile dir = createSrc();
    String url = dir.getUrl();

    ContentFolder f = entry.addSourceFolder(dir, false);
    assertEquals(dir, f.getFile());
    assertEquals(url, f.getUrl());

    VfsTestUtil.deleteFile(dir);
    assertNull(f.getFile());
    assertEquals(url, f.getUrl());

    dir = createSrc();
    assertEquals(dir, f.getFile());
    assertEquals(url, f.getUrl());
  }


  @Test


  public void testCreationOfSourceFolderWithUrl() {
    VirtualFile dir = createSrc();
    String url = dir.getUrl();
    VfsTestUtil.deleteFile(dir);

    ContentFolder f = entry.addSourceFolder(url, false);
    assertNull(f.getFile());
    assertEquals(url, f.getUrl());

    dir = createSrc();
    assertEquals(dir, f.getFile());
    assertEquals(url, f.getUrl());
  }

  @Test
  public void testCreationOfSourceFolderWithUrlWhenFileExists() {
    VirtualFile dir = createSrc();
    String url = dir.getUrl();

    ContentFolder f = entry.addSourceFolder(url, false);
    assertEquals(dir, f.getFile());
    assertEquals(url, f.getUrl());
  }

  @Test
  public void testCreationOfExcludedFolderWithFile() {
    VirtualFile dir = createSrc();
    String url = dir.getUrl();

    ContentFolder f = entry.addExcludeFolder(dir);
    assertEquals(dir, f.getFile());
    assertEquals(url, f.getUrl());

    VfsTestUtil.deleteFile(dir);
    assertNull(f.getFile());
    assertEquals(url, f.getUrl());

    dir = createSrc();
    assertEquals(dir, f.getFile());
    assertEquals(url, f.getUrl());
  }

  @NotNull
  private VirtualFile createSrc() {
    return createChildDirectory(root, "src");
  }

  @Test
  public void testCreationOfExcludedFolderWithUrl() {
    VirtualFile dir = createSrc();
    String url = dir.getUrl();
    VfsTestUtil.deleteFile(dir);

    ContentFolder f = entry.addExcludeFolder(url);
    assertNull(f.getFile());
    assertEquals(url, f.getUrl());

    dir = createSrc();
    assertEquals(dir, f.getFile());
    assertEquals(url, f.getUrl());
  }

  @Test
  public void testCreationOfExcludedFolderWithUrlWhenFileExists() {
    VirtualFile dir = createSrc();
    String url = dir.getUrl();

    ContentFolder f = entry.addExcludeFolder(url);
    assertEquals(dir, f.getFile());
    assertEquals(url, f.getUrl());
  }

  @Test
  public void testAddingExcludeWithoutSlash() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    VirtualFile dir = createSrc();
    String url = dir.getUrl();

    Ref<ContentEntry> ref = Ref.create();
    ModuleRootModificationUtil.updateModel(fixture.getModule(), model -> {
      var entry = model.addContentEntry(url + "/");
      ref.set(entry);
    });

    ContentFolder f = ref.get().addExcludeFolder(url);
    assertEquals(dir, f.getFile());
    assertEquals(url, f.getUrl());
  }
}
