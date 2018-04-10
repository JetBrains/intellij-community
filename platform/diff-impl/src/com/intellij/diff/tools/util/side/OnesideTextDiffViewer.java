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
package com.intellij.diff.tools.util.side;

import com.intellij.diff.DiffContext;
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
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.LineCol;
import com.intellij.diff.util.Side;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.pom.Navigatable;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

public abstract class OnesideTextDiffViewer extends OnesideDiffViewer<TextEditorHolder> {
  @NotNull private final List<? extends EditorEx> myEditableEditors;

  @NotNull protected final SetEditorSettingsAction myEditorSettingsAction;

  public OnesideTextDiffViewer(@NotNull DiffContext context, @NotNull ContentDiffRequest request) {
    super(context, request, TextEditorHolder.TextEditorHolderFactory.INSTANCE);

    myEditableEditors = TextDiffViewerUtil.getEditableEditors(getEditors());

    myEditorSettingsAction = new SetEditorSettingsAction(getTextSettings(), getEditors());
    myEditorSettingsAction.applyDefaults();

    new MyOpenInEditorWithMouseAction().install(getEditors());

    DiffUtil.installLineConvertor(getEditor(), getContent());
  }

  @Override
  @CalledInAwt
  protected void onInit() {
    super.onInit();
    installEditorListeners();
  }

  @Override
  @CalledInAwt
  protected void onDispose() {
    destroyEditorListeners();
    super.onDispose();
  }

  @NotNull
  @Override
  protected TextEditorHolder createEditorHolder(@NotNull EditorHolderFactory<TextEditorHolder> factory) {
    TextEditorHolder holder = super.createEditorHolder(factory);

    boolean[] forceReadOnly = TextDiffViewerUtil.checkForceReadOnly(myContext, myRequest);
    if (forceReadOnly[0]) holder.getEditor().setViewer(true);

    return holder;
  }

  @Nullable
  @Override
  protected JComponent createTitle() {
    List<JComponent> textTitles = DiffUtil.createTextTitles(myRequest, ContainerUtil.list(getEditor(), getEditor()));
    return getSide().select(textTitles);
  }

  //
  // Diff
  //

  @NotNull
  public TextDiffSettings getTextSettings() {
    return TextDiffViewerUtil.getTextSettings(myContext);
  }

  @NotNull
  protected List<AnAction> createEditorPopupActions() {
    return TextDiffViewerUtil.createEditorPopupActions();
  }

  //
  // Listeners
  //

  @CalledInAwt
  protected void installEditorListeners() {
    new TextDiffViewerUtil.EditorActionsPopup(createEditorPopupActions()).install(getEditors());
  }

  @CalledInAwt
  protected void destroyEditorListeners() {
  }

  //
  // Getters
  //

  @NotNull
  public List<? extends EditorEx> getEditors() {
    return Collections.singletonList(getEditor());
  }

  @NotNull
  protected List<? extends EditorEx> getEditableEditors() {
    return myEditableEditors;
  }

  @NotNull
  public EditorEx getEditor() {
    return getEditorHolder().getEditor();
  }

  @NotNull
  @Override
  public DocumentContent getContent() {
    //noinspection unchecked
    return (DocumentContent)super.getContent();
  }

  //
  // Abstract
  //

  @CalledInAwt
  protected void scrollToLine(int line) {
    DiffUtil.scrollEditor(getEditor(), line, false);
  }

  //
  // Misc
  //

  @Nullable
  @Override
  protected Navigatable getNavigatable() {
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

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (DiffDataKeys.CURRENT_EDITOR.is(dataId)) {
      return getEditor();
    }
    return super.getData(dataId);
  }

  protected abstract class MyInitialScrollPositionHelper extends InitialScrollPositionSupport.TwosideInitialScrollHelper {
    @NotNull
    @Override
    protected List<? extends Editor> getEditors() {
      return OnesideTextDiffViewer.this.getEditors();
    }

    @Override
    protected void disableSyncScroll(boolean value) {
    }

    @Override
    protected boolean doScrollToLine() {
      if (myScrollToLine == null) return false;
      Side side = myScrollToLine.first;
      if (side != getSide()) return false;

      scrollToLine(myScrollToLine.second);
      return true;
    }
  }
}
