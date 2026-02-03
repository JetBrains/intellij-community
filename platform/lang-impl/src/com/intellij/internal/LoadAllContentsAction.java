// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.internal;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VFileProperty;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
final class LoadAllContentsAction extends AnAction implements DumbAware {

  private static final Logger LOG = Logger.getInstance(LoadAllContentsAction.class);

  private final AtomicInteger count = new AtomicInteger();
  private final AtomicLong totalSize = new AtomicLong();

  LoadAllContentsAction() {
    super(InternalActionsBundle.messagePointer("action.AnAction.text.load.all.files.content"),
          InternalActionsBundle.messagePointer("action.AnAction.description.load.all.files.content"));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) {
      return;
    }

    String m = "Started loading content";
    LOG.info(m);
    System.out.println(m);
    long start = System.currentTimeMillis();

    count.set(0);
    totalSize.set(0);
    ApplicationManagerEx.getApplicationEx().runProcessWithProgressSynchronously(() -> {
      ProjectRootManager.getInstance(project).getFileIndex().iterateContent(fileOrDir -> {
        if (fileOrDir.isDirectory() || fileOrDir.is(VFileProperty.SPECIAL)) {
          return true;
        }
        try {
          count.incrementAndGet();
          byte[] bytes = FileUtil.loadFileBytes(new File(fileOrDir.getPath()));
          totalSize.addAndGet(bytes.length);
          ProgressManager.getInstance().getProgressIndicator().setText(fileOrDir.getPresentableUrl());
        }
        catch (IOException e1) {
          LOG.error(e1);
        }
        return true;
      });
    }, "Loading", false, project);

    long end = System.currentTimeMillis();
    String message = "Finished loading content of " + count + " files. " +
                     "Total size=" + StringUtil.formatFileSize(totalSize.get()) + ". " +
                     "Elapsed=" + ((end - start) / 1000) + "sec.";
    LOG.info(message);
    System.out.println(message);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(final @NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(e.getProject() != null);
  }
}