// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.actions;

import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffRequestFactory;
import com.intellij.diff.InvalidDiffRequestException;
import com.intellij.diff.merge.MergeRequest;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class MergeFilesAction extends AnAction implements DumbAware {
  @Override
  public void update(@NotNull AnActionEvent e) {
    DataContext context = e.getDataContext();
    Project project = CommonDataKeys.PROJECT.getData(context);
    if (project == null){
      e.getPresentation().setEnabled(false);
      return;
    }
    VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(context);
    if (files == null || files.length != 3){
      e.getPresentation().setEnabled(false);
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataContext context = e.getDataContext();
    VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(context);
    if (files == null || files.length != 3){
      return;
    }

    DiffRequestFactory diffRequestFactory = DiffRequestFactory.getInstance();

    try {
      Project project = CommonDataKeys.PROJECT.getData(context);

      String title = DiffBundle.message("merge.files.dialog.title");
      List<String> titles = Arrays.asList(files[0].getPresentableUrl(), files[1].getPresentableUrl(), files[2].getPresentableUrl());

      VirtualFile outputFile = files[1];
      List<VirtualFile> contents = Arrays.asList(files[0], files[1], files[2]);

      MergeRequest request = diffRequestFactory.createMergeRequestFromFiles(project, outputFile, contents, title, titles, null);
      request.putUserData(DiffUserDataKeys.HELP_ID, "cvs.merge");

      DiffManager.getInstance().showMerge(project, request);
    }
    catch (InvalidDiffRequestException err) {
      Messages.showErrorDialog(err.getMessage(), DiffBundle.message("merge.files.dialog.title"));
    }
  }
}
