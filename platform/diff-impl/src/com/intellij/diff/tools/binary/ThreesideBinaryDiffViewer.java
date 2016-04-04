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
import com.intellij.diff.tools.util.TransferableFileEditorStateSupport;
import com.intellij.diff.tools.util.side.ThreesideDiffViewer;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.EmptyRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.diff.util.DiffUtil.getDiffSettings;

public class ThreesideBinaryDiffViewer extends ThreesideDiffViewer<BinaryEditorHolder> {
  private final TransferableFileEditorStateSupport myTransferableStateSupport;

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
    List<AnAction> group = new ArrayList<AnAction>();
    group.add(myTransferableStateSupport.createToggleAction());
    group.addAll(super.createToolbarActions());
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

