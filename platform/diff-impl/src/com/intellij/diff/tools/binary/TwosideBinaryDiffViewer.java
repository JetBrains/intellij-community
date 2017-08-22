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
import com.intellij.diff.tools.util.TransferableFileEditorStateSupport;
import com.intellij.diff.tools.util.side.TwosideDiffViewer;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.Side;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.intellij.diff.util.DiffUtil.getDiffSettings;

public class TwosideBinaryDiffViewer extends TwosideDiffViewer<BinaryEditorHolder> {
  @NotNull private final TransferableFileEditorStateSupport myTransferableStateSupport;
  @NotNull private final StatusPanel myStatusPanel;

  public TwosideBinaryDiffViewer(@NotNull DiffContext context, @NotNull DiffRequest request) {
    super(context, (ContentDiffRequest)request, BinaryEditorHolder.BinaryEditorHolderFactory.INSTANCE);

    myStatusPanel = new StatusPanel();
    new MyFocusOppositePaneAction().install(myPanel);

    myContentPanel.setTopAction(new MyAcceptSideAction(Side.LEFT));
    myContentPanel.setBottomAction(new MyAcceptSideAction(Side.RIGHT));

    myTransferableStateSupport = new TransferableFileEditorStateSupport(getDiffSettings(context), getEditorHolders(), this);
  }

  @Override
  protected void processContextHints() {
    super.processContextHints();
    myTransferableStateSupport.processContextHints(myRequest, myContext);
  }

  @Override
  protected void updateContextHints() {
    super.updateContextHints();
    myTransferableStateSupport.updateContextHints(myRequest, myContext);
  }

  @Override
  protected List<AnAction> createToolbarActions() {
    List<AnAction> group = new ArrayList<>();

    group.add(new MyAcceptSideAction(Side.LEFT));
    group.add(new MyAcceptSideAction(Side.RIGHT));

    group.add(Separator.getInstance());
    group.add(myTransferableStateSupport.createToggleAction());
    group.addAll(super.createToolbarActions());

    return group;
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

      final JComponent notification = ReadAction.compute(() -> {
        if (!file1.isValid() || !file2.isValid()) {
          return DiffNotifications.createError();
        }

        if (FileUtilRt.isTooLarge(file1.getLength()) ||
            FileUtilRt.isTooLarge(file2.getLength())) {
          return DiffNotifications.createNotification("Files are too large to compare");
        }

        try {
          // we can't use getInputStream() here because we can't restore BOM marker
          // (getBom() can return null for binary files, while getInputStream() strips BOM for all files).
          // It can be made for files from VFS that implements FileSystemInterface though.
          byte[] bytes1 = file1.contentsToByteArray();
          byte[] bytes2 = file2.contentsToByteArray();
          return Arrays.equals(bytes1, bytes2) ? DiffNotifications.createEqualContents() : null;
        }
        catch (IOException e) {
          LOG.warn(e);
          return null;
        }
      });

      return applyNotification(notification);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      LOG.error(e);
      return applyNotification(DiffNotifications.createError());
    }
  }

  @NotNull
  private Runnable applyNotification(@Nullable final JComponent notification) {
    return () -> {
      clearDiffPresentation();
      if (notification != null) myPanel.addNotification(notification);
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

  private class MyAcceptSideAction extends DumbAwareAction {
    @NotNull private final Side myBaseSide;

    public MyAcceptSideAction(@NotNull Side baseSide) {
      myBaseSide = baseSide;
      getTemplatePresentation().setText("Copy Content to " + baseSide.select("Right", "Left"));
      getTemplatePresentation().setIcon(baseSide.select(AllIcons.Vcs.Arrow_right, AllIcons.Vcs.Arrow_left));
      setShortcutSet(ActionManager.getInstance().getAction(baseSide.select("Diff.ApplyLeftSide", "Diff.ApplyRightSide")).getShortcutSet());
    }

    @Override
    public void update(AnActionEvent e) {
      VirtualFile baseFile = getContentFile(myBaseSide);
      VirtualFile targetFile = getContentFile(myBaseSide.other());

      boolean enabled = baseFile != null && targetFile != null && targetFile.isWritable();
      e.getPresentation().setEnabledAndVisible(enabled);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final VirtualFile baseFile = getContentFile(myBaseSide);
      final VirtualFile targetFile = getContentFile(myBaseSide.other());
      assert baseFile != null && targetFile != null;

      try {
        WriteAction.run(() -> {
          targetFile.setBinaryContent(baseFile.contentsToByteArray());
        });
      }
      catch (IOException err) {
        LOG.warn(err);
        Messages.showErrorDialog(getProject(), err.getMessage(), "Can't Copy File");
      }
    }

    @Nullable
    private VirtualFile getContentFile(@NotNull Side side) {
      DiffContent content = side.select(myRequest.getContents());
      VirtualFile file = content instanceof FileContent ? ((FileContent)content).getFile() : null;
      return file != null && file.isValid() ? file : null;
    }
  }

  private class MyFocusOppositePaneAction extends FocusOppositePaneAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      setCurrentSide(getCurrentSide().other());
      DiffUtil.requestFocus(getProject(), getPreferredFocusedComponent());
    }
  }
}
