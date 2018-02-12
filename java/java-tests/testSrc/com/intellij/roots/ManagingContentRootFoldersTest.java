/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ContentFolder;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public class ManagingContentRootFoldersTest extends IdeaTestCase {
  private VirtualFile root;
  private ContentEntry entry;
  private ModifiableRootModel myModel;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ApplicationManager.getApplication().runWriteAction(() -> {
      initContentRoot();
      initModifiableModel();
    });
  }

  @Override
  protected void tearDown() throws Exception {
    if (myModel != null && myModel.isWritable()) {
      myModel.dispose();
    }
    myModel = null;
    entry = null;
    super.tearDown();
  }

  private void initContentRoot() {
    try {
      File dir = createTempDirectory();
      root = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir);
      PsiTestUtil.addContentRoot(myModule, root);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void initModifiableModel() {
    myModel = ModuleRootManager.getInstance(myModule).getModifiableModel();
    for (ContentEntry e : myModel.getContentEntries()) {
      if (Comparing.equal(e.getFile(), root)) entry = e;
    }
  }

  public void testCreationOfSourceFolderWithFile() {
    VirtualFile dir = createSrc();
    String url = dir.getUrl();

    ContentFolder f = entry.addSourceFolder(dir, false);
    assertEquals(dir, f.getFile());
    assertEquals(url, f.getUrl());

    delete(dir);
    assertNull(f.getFile());
    assertEquals(url, f.getUrl());

    dir = createSrc();
    assertEquals(dir, f.getFile());
    assertEquals(url, f.getUrl());
  }


  public void testCreationOfSourceFolderWithUrl() {
    VirtualFile dir = createSrc();
    String url = dir.getUrl();
    delete(dir);

    ContentFolder f = entry.addSourceFolder(url, false);
    assertNull(f.getFile());
    assertEquals(url, f.getUrl());

    dir = createSrc();
    assertEquals(dir, f.getFile());
    assertEquals(url, f.getUrl());
  }

  public void testCreationOfSourceFolderWithUrlWhenFileExists() {
    VirtualFile dir = createSrc();
    String url = dir.getUrl();

    ContentFolder f = entry.addSourceFolder(url, false);
    assertEquals(dir, f.getFile());
    assertEquals(url, f.getUrl());
  }

  public void testCreationOfExcludedFolderWithFile() {
    VirtualFile dir = createSrc();
    String url = dir.getUrl();

    ContentFolder f = entry.addExcludeFolder(dir);
    assertEquals(dir, f.getFile());
    assertEquals(url, f.getUrl());

    delete(dir);
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

  public void testCreationOfExcludedFolderWithUrl() {
    VirtualFile dir = createSrc();
    String url = dir.getUrl();
    delete(dir);

    ContentFolder f = entry.addExcludeFolder(url);
    assertNull(f.getFile());
    assertEquals(url, f.getUrl());

    dir = createSrc();
    assertEquals(dir, f.getFile());
    assertEquals(url, f.getUrl());
  }

  public void testCreationOfExcludedFolderWithUrlWhenFileExists() {
    VirtualFile dir = createSrc();
    String url = dir.getUrl();

    ContentFolder f = entry.addExcludeFolder(url);
    assertEquals(dir, f.getFile());
    assertEquals(url, f.getUrl());
  }
}
