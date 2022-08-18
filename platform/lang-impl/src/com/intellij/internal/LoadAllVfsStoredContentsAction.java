// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.internal;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
final class LoadAllVfsStoredContentsAction extends AnAction implements DumbAware {

  private static final Logger LOG = Logger.getInstance(LoadAllVfsStoredContentsAction.class);

  private final AtomicInteger count = new AtomicInteger();
  private final AtomicLong totalSize = new AtomicLong();

  LoadAllVfsStoredContentsAction() {
    super(InternalActionsBundle.messagePointer("action.AnAction.text.load.all.virtual.files.content"),
          InternalActionsBundle.messagePointer("action.AnAction.description.load.all.virtual.files.content"),
          null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    ApplicationEx application = ApplicationManagerEx.getApplicationEx();
    String m = "Started loading content";
    LOG.info(m);
    System.out.println(m);
    long start = System.currentTimeMillis();

    count.set(0);
    totalSize.set(0);
    application.runProcessWithProgressSynchronously(() -> {
      PersistentFS vfs = (PersistentFS)application.getComponent(ManagingFS.class);
      VirtualFile[] roots = vfs.getRoots();
      for (VirtualFile root : roots) {
        iterateCached(root);
      }
    }, "Loading", false, null);

    long end = System.currentTimeMillis();
    String message = "Finished loading content of " + count + " files. " +
                     "Total size=" + StringUtil.formatFileSize(totalSize.get()) + ". " +
                     "Elapsed=" + ((end - start) / 1000) + "sec.";
    LOG.info(message);
    System.out.println(message);
  }

  private void iterateCached(VirtualFile root) {
    processFile((NewVirtualFile)root);
    Collection<VirtualFile> children = ((NewVirtualFile)root).getCachedChildren();
    for (VirtualFile child : children) {
      iterateCached(child);
    }
  }

  public boolean processFile(NewVirtualFile file) {
    if (file.isDirectory() || file.is(VFileProperty.SPECIAL)) {
      return true;
    }
    try {
      try (InputStream stream = PersistentFS.getInstance().getInputStream(file)) {
        // check if it's really cached in VFS
        if (!(stream instanceof DataInputStream)) return true;
        byte[] bytes = FileUtil.loadBytes(stream);
        totalSize.addAndGet(bytes.length);
        count.incrementAndGet();
        ProgressManager.getInstance().getProgressIndicator().setText(file.getPresentableUrl());
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
    return true;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(e.getProject() != null);
  }
}