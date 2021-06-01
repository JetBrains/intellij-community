// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.binary;

import com.intellij.diff.DiffContext;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.holders.BinaryEditorHolder;
import com.intellij.diff.tools.util.TransferableFileEditorStateSupport;
import com.intellij.diff.tools.util.side.ThreesideDiffViewer;
import com.intellij.icons.AllIcons;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.EmptyRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.diff.util.DiffUtil.getDiffSettings;

public class ThreesideBinaryDiffViewer extends ThreesideDiffViewer<BinaryEditorHolder> {
  @NotNull private final TransferableFileEditorStateSupport myTransferableStateSupport;

  public ThreesideBinaryDiffViewer(@NotNull DiffContext context, @NotNull DiffRequest request) {
    super(context, (ContentDiffRequest)request, BinaryEditorHolder.BinaryEditorHolderFactory.INSTANCE);

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

    DefaultActionGroup diffGroup = DefaultActionGroup.createPopupGroup(() -> ActionsBundle.message("group.compare.contents.text"));
    diffGroup.getTemplatePresentation().setIcon(AllIcons.Actions.Diff);
    diffGroup.add(Separator.create(ActionsBundle.message("group.compare.contents.text")));
    diffGroup.add(new ShowPartialDiffAction(PartialDiffMode.MIDDLE_LEFT, false));
    diffGroup.add(new ShowPartialDiffAction(PartialDiffMode.MIDDLE_RIGHT, false));
    diffGroup.add(new ShowPartialDiffAction(PartialDiffMode.LEFT_RIGHT, false));
    group.add(diffGroup);
    group.add(Separator.getInstance());

    group.add(myTransferableStateSupport.createToggleAction());
    group.addAll(super.createToolbarActions());
    group.add(ActionManager.getInstance().getAction("Diff.Binary.Settings"));
    return group;
  }

  @Override
  @NotNull
  protected Runnable performRediff(@NotNull final ProgressIndicator indicator) {
    return EmptyRunnable.INSTANCE;
  }

  public static boolean canShowRequest(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return ThreesideDiffViewer.canShowRequest(context, request, BinaryEditorHolder.BinaryEditorHolderFactory.INSTANCE);
  }
}
