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
import com.intellij.diff.tools.util.SyncScrollSupport;
import com.intellij.diff.tools.util.SyncScrollSupport.ThreesideSyncScrollSupport;
import com.intellij.diff.tools.util.base.InitialScrollPositionSupport;
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder;
import com.intellij.diff.tools.util.base.TextDiffViewerUtil;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.Side;
import com.intellij.diff.util.ThreeSide;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public abstract class ThreesideTextDiffViewer extends ThreesideDiffViewer<TextEditorHolder> {
  public static final Logger LOG = Logger.getInstance(ThreesideTextDiffViewer.class);

  @Nullable private List<? extends EditorEx> myEditors;
  @NotNull private final List<? extends EditorEx> myEditableEditors;

  @NotNull private final MyVisibleAreaListener myVisibleAreaListener1 = new MyVisibleAreaListener(Side.LEFT);
  @NotNull private final MyVisibleAreaListener myVisibleAreaListener2 = new MyVisibleAreaListener(Side.RIGHT);
  @Nullable protected ThreesideSyncScrollSupport mySyncScrollSupport;

  @NotNull protected final SetEditorSettingsAction myEditorSettingsAction;

  public ThreesideTextDiffViewer(@NotNull DiffContext context, @NotNull ContentDiffRequest request) {
    super(context, request, TextEditorHolder.TextEditorHolderFactory.INSTANCE);

    //new MyFocusOppositePaneAction().setupAction(myPanel, this); // TODO

    myEditorSettingsAction = new SetEditorSettingsAction(getTextSettings(), getEditors());
    myEditorSettingsAction.applyDefaults();

    new MyOpenInEditorWithMouseAction().register(getEditors());

    myEditableEditors = TextDiffViewerUtil.getEditableEditors(getEditors());
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
  protected List<TextEditorHolder> createEditorHolders(@NotNull EditorHolderFactory<TextEditorHolder> factory) {
    List<TextEditorHolder> holders = super.createEditorHolders(factory);

    boolean[] forceReadOnly = TextDiffViewerUtil.checkForceReadOnly(myContext, myRequest);
    for (int i = 0; i < 3; i++) {
      if (forceReadOnly[i]) holders.get(i).getEditor().setViewer(true);
    }

    ThreeSide.LEFT.select(holders).getEditor().setVerticalScrollbarOrientation(EditorEx.VERTICAL_SCROLLBAR_LEFT);
    ((EditorMarkupModel)ThreeSide.BASE.select(holders).getEditor().getMarkupModel()).setErrorStripeVisible(false);

    if (Registry.is("diff.divider.repainting.disable.blitting")) {
      for (TextEditorHolder holder : holders) {
        holder.getEditor().getScrollPane().getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
      }
    }

    return holders;
  }

  @NotNull
  @Override
  protected List<JComponent> createTitles() {
    return DiffUtil.createTextTitles(myRequest, getEditors());
  }

  //
  // Listeners
  //

  @CalledInAwt
  protected void installEditorListeners() {
    new TextDiffViewerUtil.EditorActionsPopup(createEditorPopupActions()).install(getEditors());

    new TextDiffViewerUtil.EditorFontSizeSynchronizer(getEditors()).install(this);

    getEditor(ThreeSide.LEFT).getScrollingModel().addVisibleAreaListener(myVisibleAreaListener1);
    getEditor(ThreeSide.BASE).getScrollingModel().addVisibleAreaListener(myVisibleAreaListener1);

    getEditor(ThreeSide.BASE).getScrollingModel().addVisibleAreaListener(myVisibleAreaListener2);
    getEditor(ThreeSide.RIGHT).getScrollingModel().addVisibleAreaListener(myVisibleAreaListener2);

    SyncScrollSupport.SyncScrollable scrollable1 = getSyncScrollable(Side.LEFT);
    SyncScrollSupport.SyncScrollable scrollable2 = getSyncScrollable(Side.RIGHT);
    if (scrollable1 != null && scrollable2 != null) {
      mySyncScrollSupport = new ThreesideSyncScrollSupport(getEditors(), scrollable1, scrollable2);
    }
  }

  @CalledInAwt
  public void destroyEditorListeners() {
    getEditor(ThreeSide.LEFT).getScrollingModel().removeVisibleAreaListener(myVisibleAreaListener1);
    getEditor(ThreeSide.BASE).getScrollingModel().removeVisibleAreaListener(myVisibleAreaListener1);

    getEditor(ThreeSide.BASE).getScrollingModel().removeVisibleAreaListener(myVisibleAreaListener2);
    getEditor(ThreeSide.RIGHT).getScrollingModel().removeVisibleAreaListener(myVisibleAreaListener2);

    mySyncScrollSupport = null;
  }

  protected void disableSyncScrollSupport(boolean disable) {
    if (mySyncScrollSupport != null) {
      mySyncScrollSupport.setDisabled(disable);
    }
  }

  //
  // Diff
  //

  @NotNull
  public TextDiffSettingsHolder.TextDiffSettings getTextSettings() {
    return TextDiffViewerUtil.getTextSettings(myContext);
  }

  @NotNull
  protected List<AnAction> createEditorPopupActions() {
    return TextDiffViewerUtil.createEditorPopupActions();
  }

  @Override
  protected void onDocumentChange(@NotNull DocumentEvent event) {
    super.onDocumentChange(event);
    myContentPanel.repaintDividers();
  }

  //
  // Getters
  //

  @NotNull
  public EditorEx getCurrentEditor() {
    return getEditor(getCurrentSide());
  }

  @NotNull
  public DocumentContent getCurrentContent() {
    return getContent(getCurrentSide());
  }

  @NotNull
  protected List<? extends DocumentContent> getContents() {
    //noinspection unchecked
    return (List)myRequest.getContents();
  }

  @NotNull
  public List<? extends EditorEx> getEditors() {
    if (myEditors == null) {
      myEditors = ContainerUtil.map(getEditorHolders(), new Function<TextEditorHolder, EditorEx>() {
        @Override
        public EditorEx fun(TextEditorHolder holder) {
          return holder.getEditor();
        }
      });
    }
    return myEditors;
  }

  @NotNull
  protected List<? extends EditorEx> getEditableEditors() {
    return myEditableEditors;
  }

  @NotNull
  public EditorEx getEditor(@NotNull ThreeSide side) {
    return side.select(getEditors());
  }

  @NotNull
  public DocumentContent getContent(@NotNull ThreeSide side) {
    return side.select(getContents());
  }

  //
  // Abstract
  //

  @CalledInAwt
  protected void scrollToLine(@NotNull ThreeSide side, int line) {
    DiffUtil.scrollEditor(getEditor(side), line, false);
    setCurrentSide(side);
  }

  @Nullable
  protected abstract SyncScrollSupport.SyncScrollable getSyncScrollable(@NotNull Side side);

  //
  // Misc
  //

  @Nullable
  @Override
  protected OpenFileDescriptor getOpenFileDescriptor() {
    int offset = getCurrentEditor().getCaretModel().getOffset();
    return getCurrentContent().getOpenFileDescriptor(offset);
  }

  public static boolean canShowRequest(@NotNull DiffContext context, @NotNull DiffRequest request) {
    return ThreesideDiffViewer.canShowRequest(context, request, TextEditorHolder.TextEditorHolderFactory.INSTANCE);
  }

  //
  // Actions
  //

  private class MyOpenInEditorWithMouseAction extends OpenInEditorWithMouseAction {
    @Override
    protected OpenFileDescriptor getDescriptor(@NotNull Editor editor, int line) {
      ThreeSide side = null;
      if (editor == getEditor(ThreeSide.LEFT)) side = ThreeSide.LEFT;
      if (editor == getEditor(ThreeSide.RIGHT)) side = ThreeSide.RIGHT;
      if (editor == getEditor(ThreeSide.BASE)) side = ThreeSide.BASE;
      if (side == null) return null;

      int offset = editor.logicalPositionToOffset(new LogicalPosition(line, 0));
      return getContent(side).getOpenFileDescriptor(offset);
    }
  }

  protected class MyToggleAutoScrollAction extends TextDiffViewerUtil.ToggleAutoScrollAction {
    public MyToggleAutoScrollAction() {
      super(getTextSettings());
    }
  }

  //
  // Helpers
  //

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (DiffDataKeys.CURRENT_EDITOR.is(dataId)) {
      return getCurrentEditor();
    }
    return super.getData(dataId);
  }
  
  private class MyVisibleAreaListener implements VisibleAreaListener {
    @NotNull Side mySide;

    public MyVisibleAreaListener(@NotNull Side side) {
      mySide = side;
    }

    @Override
    public void visibleAreaChanged(VisibleAreaEvent e) {
      if (mySyncScrollSupport != null) mySyncScrollSupport.visibleAreaChanged(e);
      if (Registry.is("diff.divider.repainting.fix")) {
        myContentPanel.repaint();
      }
      else {
        myContentPanel.repaintDivider(mySide);
      }
    }
  }

  protected abstract class MyInitialScrollPositionHelper extends InitialScrollPositionSupport.ThreesideInitialScrollHelper {
    @NotNull
    @Override
    protected List<? extends Editor> getEditors() {
      return ThreesideTextDiffViewer.this.getEditors();
    }

    @Override
    protected void disableSyncScroll(boolean value) {
      disableSyncScrollSupport(value);
    }

    @Override
    protected boolean doScrollToLine() {
      if (myScrollToLine == null) return false;

      scrollToLine(myScrollToLine.first, myScrollToLine.second);
      return true;
    }
  }
}
