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
package com.intellij.diff.tools.binary;

import com.intellij.diff.DiffContext;
import com.intellij.diff.actions.impl.FocusOppositePaneAction;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.FileContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.holders.BinaryEditorHolder;
import com.intellij.diff.tools.util.DiffNotifications;
import com.intellij.diff.tools.util.StatusPanel;
import com.intellij.diff.tools.util.side.TwosideDiffViewer;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class TwosideBinaryDiffViewer extends TwosideDiffViewer<BinaryEditorHolder> {
  public static final Logger LOG = Logger.getInstance(TwosideBinaryDiffViewer.class);

  @NotNull private final StatusPanel myStatusPanel;

  public TwosideBinaryDiffViewer(@NotNull DiffContext context, @NotNull DiffRequest request) {
    super(context, (ContentDiffRequest)request, BinaryEditorHolder.BinaryEditorHolderFactory.INSTANCE);

    myStatusPanel = new StatusPanel();
    new MyFocusOppositePaneAction().setupAction(myPanel);
  }

  //
  // Diff
  //

  @Override
  protected void onSlowRediff() {
    super.onSlowRediff();
    myStatusPanel.setBusy(true);
  }

  @Override
  @NotNull
  protected Runnable performRediff(@NotNull final ProgressIndicator indicator) {
    try {
      indicator.checkCanceled();

      List<DiffContent> contents = myRequest.getContents();
      if (!(contents.get(0) instanceof FileContent) || !(contents.get(1) instanceof FileContent)) {
        return applyNotification(null);
      }

      final VirtualFile file1 = ((FileContent)contents.get(0)).getFile();
      final VirtualFile file2 = ((FileContent)contents.get(1)).getFile();
      if (!file1.isValid() || !file2.isValid()) {
        return applyNotification(DiffNotifications.ERROR);
      }

      final boolean equal = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
        @Override
        public Boolean compute() {
          try {
            // we can't use getInputStream() here because we can't restore BOM marker
            // (getBom() can return null for binary files, while getInputStream() strips BOM for all files).
            // It can be made for files from VFS that implements FileSystemInterface though.
            byte[] bytes1 = file1.contentsToByteArray();
            byte[] bytes2 = file2.contentsToByteArray();
            return Arrays.equals(bytes1, bytes2);
          }
          catch (IOException e) {
            LOG.warn(e);
            return false;
          }
        }
      });

      return applyNotification(equal ? DiffNotifications.EQUAL_CONTENTS : null);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      LOG.error(e);
      return applyNotification(DiffNotifications.ERROR);
    }
  }

  @NotNull
  private Runnable applyNotification(@Nullable final JComponent notification) {
    return new Runnable() {
      @Override
      public void run() {
        clearDiffPresentation();
        if (notification != null) myPanel.addNotification(notification);
      }
    };
  }

  private void clearDiffPresentation() {
    myStatusPanel.setBusy(false);
    myPanel.resetNotifications();
  }

  //
  // Getters
  //

  @NotNull
  FileEditor getCurrentEditor() {
    return getCurrentEditorHolder().getEditor();
  }

  @NotNull
  @Override
  protected JComponent getStatusPanel() {
    return myStatusPanel;
  }

  //
  // Misc
  //

  public static boolean canShowRequest(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return TwosideDiffViewer.canShowRequest(context, request, BinaryEditorHolder.BinaryEditorHolderFactory.INSTANCE);
  }

  //
  // Actions
  //

  private class MyFocusOppositePaneAction extends FocusOppositePaneAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      setCurrentSide(getCurrentSide().other());
      myContext.requestFocus();
    }
  }
}
