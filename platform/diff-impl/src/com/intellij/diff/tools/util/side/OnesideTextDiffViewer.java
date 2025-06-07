// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.util.side;

import com.intellij.diff.DiffContext;
import com.intellij.diff.EditorDiffViewer;
import com.intellij.diff.actions.impl.OpenInEditorWithMouseAction;
import com.intellij.diff.actions.impl.SetEditorSettingsAction;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.tools.holders.EditorHolderFactory;
import com.intellij.diff.tools.holders.TextEditorHolder;
import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.diff.tools.util.base.InitialScrollPositionSupport;
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings;
import com.intellij.diff.tools.util.base.TextDiffViewerUtil;
import com.intellij.diff.tools.util.breadcrumbs.SimpleDiffBreadcrumbsPanel;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.LineCol;
import com.intellij.diff.util.Side;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.pom.Navigatable;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class OnesideTextDiffViewer extends OnesideDiffViewer<TextEditorHolder> implements EditorDiffViewer {
  private final @NotNull List<? extends EditorEx> myEditableEditors;

  protected final @NotNull SetEditorSettingsAction myEditorSettingsAction;

  public OnesideTextDiffViewer(@NotNull DiffContext context, @NotNull ContentDiffRequest request) {
    super(context, request, TextEditorHolder.TextEditorHolderFactory.INSTANCE);

    myEditableEditors = TextDiffViewerUtil.getEditableEditors(getEditors());

    myEditorSettingsAction = new SetEditorSettingsAction(getTextSettings(), getEditors());
    myEditorSettingsAction.applyDefaults();

    new MyOpenInEditorWithMouseAction().install(getEditors());

    DiffUtil.installLineConvertor(getEditor(), getContent());

    if (getProject() != null) {
      myContentPanel.setBreadcrumbs(new SimpleDiffBreadcrumbsPanel(getEditor(), this), getTextSettings());
    }
  }

  @Override
  @RequiresEdt
  protected void onInit() {
    super.onInit();
    installEditorListeners();
  }

  @Override
  @RequiresEdt
  protected void onDispose() {
    destroyEditorListeners();
    super.onDispose();
  }

  @ApiStatus.Internal
  @Override
  protected @NotNull TextEditorHolder createEditorHolder(@NotNull EditorHolderFactory<TextEditorHolder> factory) {
    TextEditorHolder holder = super.createEditorHolder(factory);

    boolean[] forceReadOnly = TextDiffViewerUtil.checkForceReadOnly(myContext, myRequest);
    if (forceReadOnly[0]) holder.getEditor().setViewer(true);

    return holder;
  }

  @Override
  protected @Nullable JComponent createTitle() {
    List<JComponent> textTitles = DiffUtil.createTextTitles(this, myRequest, Arrays.asList(getEditor(), getEditor()));
    return getSide().select(textTitles);
  }

  //
  // Diff
  //
  public @NotNull TextDiffSettings getTextSettings() {
    return TextDiffViewerUtil.getTextSettings(myContext);
  }

  protected @NotNull List<AnAction> createEditorPopupActions() {
    return TextDiffViewerUtil.createEditorPopupActions();
  }

  //
  // Listeners
  //

  @RequiresEdt
  protected void installEditorListeners() {
    new TextDiffViewerUtil.EditorActionsPopup(createEditorPopupActions()).install(getEditors(), myPanel);
  }

  @RequiresEdt
  protected void destroyEditorListeners() {
  }

  //
  // Getters
  //

  @Override
  public @NotNull List<? extends EditorEx> getEditors() {
    return Collections.singletonList(getEditor());
  }

  protected @NotNull List<? extends EditorEx> getEditableEditors() {
    return myEditableEditors;
  }

  public @NotNull EditorEx getEditor() {
    return getEditorHolder().getEditor();
  }

  @Override
  public @NotNull DocumentContent getContent() {
    return (DocumentContent)super.getContent();
  }

  //
  // Abstract
  //

  @RequiresEdt
  public void scrollToLine(int line) {
    DiffUtil.scrollEditor(getEditor(), line, false);
  }

  //
  // Misc
  //

  @Override
  public @Nullable Navigatable getNavigatable() {
    return getContent().getNavigatable(LineCol.fromCaret(getEditor()));
  }

  public static boolean canShowRequest(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return OnesideDiffViewer.canShowRequest(context, request, TextEditorHolder.TextEditorHolderFactory.INSTANCE);
  }

  //
  // Actions
  //

  private class MyOpenInEditorWithMouseAction extends OpenInEditorWithMouseAction {
    @Override
    protected Navigatable getNavigatable(@NotNull Editor editor, int line) {
      if (editor != getEditor()) return null;
      return getContent().getNavigatable(new LineCol(line));
    }
  }

  //
  // Helpers
  //

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    super.uiDataSnapshot(sink);
    sink.set(DiffDataKeys.CURRENT_EDITOR, getEditor());
  }

  protected abstract class MyInitialScrollPositionHelper extends InitialScrollPositionSupport.TwosideInitialScrollHelper {
    @Override
    protected @NotNull List<? extends Editor> getEditors() {
      return OnesideTextDiffViewer.this.getEditors();
    }

    @Override
    protected void disableSyncScroll(boolean value) {
    }

    @Override
    protected boolean doScrollToLine(boolean onSlowRediff) {
      if (myScrollToLine == null) return false;
      Side side = myScrollToLine.first;
      if (side != getSide()) return false;

      scrollToLine(myScrollToLine.second);
      return true;
    }
  }
}
