// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.integration;

import com.intellij.history.core.*;
import com.intellij.history.core.changes.ChangeSet;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.core.tree.RootEntry;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.Clock;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.vfs.*;
import com.intellij.platform.lvcs.impl.RevisionId;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public abstract class IntegrationTestCase extends HeavyPlatformTestCase {
  protected static final int TIMESTAMP_INCREMENT = 3000;
  protected static final String FILTERED_DIR_NAME = "CVS";

  protected VirtualFile myRoot;
  protected IdeaGateway myGateway;

  // let it be as if someone (e.g. dumb mode indexing) has loaded the content so it's available to local history
  protected static void loadContent(VirtualFile f) throws IOException {
    f.contentsToByteArray();
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    LocalHistoryImpl.getInstanceImpl().cleanupForNextTest();

    Clock.reset();
    Paths.useSystemCaseSensitivity();

    myGateway = new IdeaGateway();

    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        setUpInWriteAction();
      }
      catch (Exception e) {
        ExceptionUtil.rethrow(e);
      }
    });
  }

  protected void setUpInWriteAction() throws Exception {
    myRoot = getTempDir().createVirtualDir();
    PsiTestUtil.addContentRoot(myModule, myRoot);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      Clock.reset();
      Paths.useSystemCaseSensitivity();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  protected @NotNull VirtualFile createFile(@NotNull String name) throws IOException {
    return createFile(name, null);
  }

  // tests fail if file created via API, so, refreshAndFindFileByNioFile is used
  protected @NotNull VirtualFile createFile(@NotNull String name, @Nullable String content) throws IOException {
    Path file = myRoot.toNioPath().resolve(name);
    if (content == null) {
      Files.createFile(NioFiles.createParentDirectories(file));
    }
    else {
      Files.writeString(NioFiles.createParentDirectories(file), content);
    }
    return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file);
  }

  protected final @NotNull VirtualFile createDirectory(@NotNull String name) {
    return VfsTestUtil.createDir(myRoot, name);
  }

  protected final void setContent(@NotNull VirtualFile f, @NotNull String content) {
    setContent(f, content, f.getTimeStamp() + TIMESTAMP_INCREMENT);
  }

  protected final void setContent(@NotNull VirtualFile f, int content) {
    setContent(f, (byte)content);
  }

  protected final void setContent(@NotNull VirtualFile f, byte content) {
    setBinaryContent(f, new byte[]{content}, -1, f.getTimeStamp() + TIMESTAMP_INCREMENT, this);
  }

  protected final void setContent(VirtualFile f, String content, long timestamp) {
    setBinaryContent(f, content.getBytes(StandardCharsets.UTF_8), -1, timestamp, this);
  }

  protected final @NotNull String createFileExternally(@NotNull String name) throws IOException {
    Path file = myRoot.toNioPath().resolve(name);
    Files.createFile(NioFiles.createParentDirectories(file));
    return file.toString().replace(File.separatorChar, '/');
  }

  protected final String createDirectoryExternally(String name) {
    File f = new File(myRoot.getPath(), name);
    assertTrue(f.getPath(), f.mkdirs() || f.isDirectory());
    return FileUtil.toSystemIndependentName(f.getPath());
  }

  protected static void setContentExternally(String path, @SuppressWarnings("SameParameterValue") String content) throws IOException {
    Path f = Path.of(path);
    Files.writeString(f, content);
    Files.setLastModifiedTime(f, FileTime.fromMillis(Files.getLastModifiedTime(f).toMillis() + 2000));
  }

  protected static void setDocumentTextFor(VirtualFile f, String text) {
    Document document = FileDocumentManager.getInstance().getDocument(f);
    assertNotNull(f.getPath(), document);
    ApplicationManager.getApplication().runWriteAction(() -> document.setText(text));
  }

  public @NotNull LocalHistoryFacade getVcs() {
    return LocalHistoryImpl.getInstanceImpl().getFacade();
  }

  public @NotNull IdeaGateway getGateway() {
    return myGateway;
  }

  /**
   * Includes revisions for all ChangeSets plus a current revision.
   */
  protected @NotNull List<RevisionId> getRevisionIdsFor(@NotNull VirtualFile f) {
    List<RevisionId> revisionIds = ContainerUtil.map(getChangesFor(f), change -> new RevisionId.ChangeSet(change.getId()));
    return ContainerUtil.concat(Arrays.asList(RevisionId.Current.INSTANCE), revisionIds);
  }

  protected @NotNull List<ChangeSet> getChangesFor(@NotNull VirtualFile f) {
    return getChangesFor(f, null);
  }

  protected @NotNull List<ChangeSet> getChangesFor(@NotNull VirtualFile f, @Nullable String pattern) {
    return LocalHistoryTestCase.collectChanges(getVcs(), f.getPath(), myProject.getLocationHash(), HistoryPathFilter.create(pattern, myProject));
  }

  protected @Nullable Entry getEntryFor(@NotNull ChangeSet changeSet, @NotNull VirtualFile f) {
    return getEntryFor(new RevisionId.ChangeSet(changeSet.getId()), f);
  }

  protected @Nullable Entry getCurrentEntry(@NotNull VirtualFile f) {
    return getEntryFor(RevisionId.Current.INSTANCE, f);
  }

  protected @Nullable Entry getEntryFor(@NotNull RevisionId revisionId, @NotNull VirtualFile f) {
    return LocalHistoryTestCase.getEntryFor(getVcs(), getRootEntry(), revisionId, f.getPath());
  }

  public @NotNull RootEntry getRootEntry() {
    return myGateway.createTransientRootEntryForPath(myGateway.getPathOrUrl(myRoot), true);
  }

  protected void addContentRoot(@NotNull String path) {
    addContentRoot(myModule, path);
  }

  protected static void addContentRoot(@NotNull Module module, @NotNull String path) {
    ApplicationManager.getApplication()
      .runWriteAction(() -> ModuleRootModificationUtil.addContentRoot(module, FileUtil.toSystemIndependentName(path)));
  }

  protected void addExcludedDir(final String path) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      ModuleRootManager rm = ModuleRootManager.getInstance(myModule);
      ModifiableRootModel m = rm.getModifiableModel();
      for (ContentEntry e : m.getContentEntries()) {
        if (!Comparing.equal(e.getFile(), myRoot)) continue;
        e.addExcludeFolder(VfsUtilCore.pathToUrl(path));
      }
      m.commit();
    });
  }

  protected @NotNull List<String> getContentFor(@NotNull VirtualFile file, @NotNull Set<Long> changeSets) {
    List<String> result = new ArrayList<>();
    LocalHistoryFacadeKt.processContents(getVcs(), myGateway, getRootEntry(), myGateway.getPathOrUrl(file), changeSets, true, (changeSet, content) -> {
      if (content != null) result.add(content);
      return Boolean.TRUE;
    });
    return result;
  }

  protected static void addFileListenerDuring(VirtualFileListener l, Runnable r) {
    VirtualFileManager.getInstance().addVirtualFileListener(l);
    try {
      r.run();
    }
    finally {
      VirtualFileManager.getInstance().removeVirtualFileListener(l);
    }
  }

  protected static void assertContent(String expected, @NotNull Entry e) {
    assertEquals(expected, getContentAsString(e));
  }

  protected static @NotNull String getContentAsString(@NotNull Entry e) {
    return new String(e.getContent().getBytes(), StandardCharsets.UTF_8);
  }
}
