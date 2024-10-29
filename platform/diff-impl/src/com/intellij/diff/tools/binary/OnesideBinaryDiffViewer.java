// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.binary;

import com.intellij.diff.DiffContext;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.holders.BinaryEditorHolder;
import com.intellij.diff.tools.util.DiffNotifications;
import com.intellij.diff.tools.util.TransferableFileEditorStateSupport;
import com.intellij.diff.tools.util.side.OnesideDiffViewer;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.diff.util.DiffUtil.getDiffSettings;
import static java.util.Collections.singletonList;

@ApiStatus.Internal
public class OnesideBinaryDiffViewer extends OnesideDiffViewer<BinaryEditorHolder> {
  @NotNull private final TransferableFileEditorStateSupport myTransferableStateSupport;

  public OnesideBinaryDiffViewer(@NotNull DiffContext context, @NotNull DiffRequest request) {
    super(context, (ContentDiffRequest)request, BinaryEditorHolder.BinaryEditorHolderFactory.INSTANCE);

    myTransferableStateSupport = new TransferableFileEditorStateSupport(getDiffSettings(context), singletonList(getEditorHolder()), this);
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
    group.add(myTransferableStateSupport.createToggleAction());
    group.addAll(super.createToolbarActions());
    group.add(ActionManager.getInstance().getAction("Diff.Binary.Settings"));
    return group;
  }

  //
  // Diff
  //

  @Override
  @NotNull
  protected Runnable performRediff(@NotNull final ProgressIndicator indicator) {
    JComponent notification = getSide().select(DiffNotifications.createRemovedContent(), DiffNotifications.createInsertedContent());
    return applyNotification(notification);
  }

  @NotNull
  private Runnable applyNotification(@Nullable final JComponent notification) {
    return () -> {
      clearDiffPresentation();
      if (notification != null) myPanel.addNotification(notification);
    };
  }

  private void clearDiffPresentation() {
    myPanel.resetNotifications();
  }

  //
  // Getters
  //

  @NotNull
  FileEditor getEditor() {
    return getEditorHolder().getEditor();
  }

  //
  // Misc
  //

  public static boolean canShowRequest(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return OnesideDiffViewer.canShowRequest(context, request, BinaryEditorHolder.BinaryEditorHolderFactory.INSTANCE);
  }
}
