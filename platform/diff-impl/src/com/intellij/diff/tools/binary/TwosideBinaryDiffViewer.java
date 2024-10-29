// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileUtil;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.diff.util.DiffUtil.getDiffSettings;

@ApiStatus.Internal
public class TwosideBinaryDiffViewer extends TwosideDiffViewer<BinaryEditorHolder> {
  @NotNull private final TransferableFileEditorStateSupport myTransferableStateSupport;
  @NotNull private final StatusPanel myStatusPanel;

  @NotNull private ComparisonData myComparisonData = ComparisonData.UNKNOWN;

  public TwosideBinaryDiffViewer(@NotNull DiffContext context, @NotNull DiffRequest request) {
    super(context, (ContentDiffRequest)request, BinaryEditorHolder.BinaryEditorHolderFactory.INSTANCE);

    myStatusPanel = new MyStatusPanel();
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

    group.add(ActionManager.getInstance().getAction("Diff.Binary.Settings"));

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
        return applyNotification(ComparisonData.UNKNOWN);
      }

      final VirtualFile file1 = ((FileContent)contents.get(0)).getFile();
      final VirtualFile file2 = ((FileContent)contents.get(1)).getFile();

      ComparisonData comparisonData = ReadAction.nonBlocking(() -> {
        if (!file1.isValid() || !file2.isValid()) {
          return ComparisonData.ERROR;
        }

        long length1 = file1.getLength();
        long length2 = file2.getLength();
        try {
          boolean contentsEquals;
          if (length1 > 0 && length2 > 0 && length1 != length2) {
            // Can't trust 0 length, at it might be a lie (and loading empty content into memory shouldn't hurt much).
            contentsEquals = false;
          }
          else if (VirtualFileUtil.isTooLarge(file1) || VirtualFileUtil.isTooLarge(file2)) {
            return new ComparisonData(ThreeState.UNSURE, () -> DiffBundle.message("error.files.too.large.to.compare.text"));
          }
          else {
            contentsEquals = DiffUtil.compareStreams(() -> DiffUtil.getFileInputStream(file1), () -> DiffUtil.getFileInputStream(file2));
          }

          return new ComparisonData(ThreeState.fromBoolean(contentsEquals),
                                    () -> contentsEquals ? DiffBundle.message("diff.contents.are.identical.message.text") : null);
        }
        catch (IOException e) {
          LOG.warn(e);
          return ComparisonData.ERROR;
        }
      })
        .wrapProgress(indicator)
        .executeSynchronously();

      return applyNotification(comparisonData);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      LOG.error(e);
      return applyNotification(ComparisonData.ERROR);
    }
  }

  @NotNull
  private Runnable applyNotification(@NotNull final ComparisonData comparisonData) {
    return () -> {
      clearDiffPresentation();

      myComparisonData = comparisonData;

      if (myComparisonData.notification.get() != null) {
        myPanel.addNotification(DiffNotifications.createNotification(myComparisonData.notification.get()));
      }
      myStatusPanel.update();
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

  private class MyStatusPanel extends StatusPanel {
    @Nullable
    @Override
    protected String getMessage() {
      if (myComparisonData.isContentsEqual == ThreeState.UNSURE) return null;
      if (myComparisonData.isContentsEqual == ThreeState.YES) {
        return DiffBundle.message("binary.diff.contents.are.identical.message.text");
      }
      else {
        return DiffBundle.message("binary.diff.contents.are.different.message.text");
      }
    }
  }

  //
  // Actions
  //

  private class MyAcceptSideAction extends DumbAwareAction {
    @NotNull private final Side myBaseSide;

    MyAcceptSideAction(@NotNull Side baseSide) {
      myBaseSide = baseSide;
      getTemplatePresentation().setText(DiffBundle.message("copy.content.to.side", baseSide.other().getIndex()));
      getTemplatePresentation().setIcon(baseSide.select(AllIcons.Vcs.Arrow_right, AllIcons.Vcs.Arrow_left));
      setShortcutSet(ActionManager.getInstance().getAction(baseSide.select("Diff.ApplyLeftSide", "Diff.ApplyRightSide")).getShortcutSet());
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      VirtualFile baseFile = getContentFile(myBaseSide);
      VirtualFile targetFile = getContentFile(myBaseSide.other());

      boolean enabled = baseFile != null && targetFile != null && targetFile.isWritable();
      e.getPresentation().setEnabledAndVisible(enabled);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final VirtualFile baseFile = getContentFile(myBaseSide);
      final VirtualFile targetFile = getContentFile(myBaseSide.other());
      assert baseFile != null && targetFile != null;

      try {
        WriteAction.run(() -> targetFile.setBinaryContent(baseFile.contentsToByteArray()));
      }
      catch (IOException err) {
        LOG.warn(err);
        Messages.showErrorDialog(getProject(), err.getMessage(), DiffBundle.message("can.t.copy.file"));
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

  private static final class ComparisonData {
    public static final ComparisonData UNKNOWN = new ComparisonData(ThreeState.UNSURE, () -> null);
    public static final ComparisonData ERROR = new ComparisonData(ThreeState.UNSURE, () -> DiffBundle.message("diff.cant.calculate.diff"));

    @NotNull public final ThreeState isContentsEqual;
    @NotNull public final NullableComputable<@Nls String> notification;

    private ComparisonData(@NotNull ThreeState isContentsEqual, @NotNull NullableComputable<@Nls String> notification) {
      this.isContentsEqual = isContentsEqual;
      this.notification = notification;
    }
  }
}
