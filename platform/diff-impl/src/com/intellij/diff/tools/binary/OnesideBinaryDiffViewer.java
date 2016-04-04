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
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.holders.BinaryEditorHolder;
import com.intellij.diff.tools.util.DiffNotifications;
import com.intellij.diff.tools.util.TransferableFileEditorStateSupport;
import com.intellij.diff.tools.util.side.OnesideDiffViewer;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.diff.util.DiffUtil.getDiffSettings;
import static java.util.Collections.singletonList;

public class OnesideBinaryDiffViewer extends OnesideDiffViewer<BinaryEditorHolder> {
  public static final Logger LOG = Logger.getInstance(OnesideBinaryDiffViewer.class);

  private final TransferableFileEditorStateSupport myTransferableStateSupport;

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
    List<AnAction> group = new ArrayList<AnAction>();
    group.add(myTransferableStateSupport.createToggleAction());
    group.addAll(super.createToolbarActions());
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
    return new Runnable() {
      @Override
      public void run() {
        clearDiffPresentation();
        if (notification != null) myPanel.addNotification(notification);
      }
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
