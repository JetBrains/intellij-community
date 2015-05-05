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
import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.DiffManager;
import com.intellij.diff.actions.impl.SetEditorSettingsAction;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.tools.holders.TextEditorHolder;
import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.diff.tools.util.FocusTrackerSupport.ThreesideFocusTrackerSupport;
import com.intellij.diff.tools.util.SimpleDiffPanel;
import com.intellij.diff.tools.util.SyncScrollSupport;
import com.intellij.diff.tools.util.SyncScrollSupport.ThreesideSyncScrollSupport;
import com.intellij.diff.tools.util.base.InitialScrollPositionSupport;
import com.intellij.diff.tools.util.base.ListenerDiffViewerBase;
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder;
import com.intellij.diff.tools.util.base.TextDiffViewerUtil;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.Side;
import com.intellij.diff.util.ThreeSide;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public abstract class ThreesideTextDiffViewer extends ListenerDiffViewerBase {
  public static final Logger LOG = Logger.getInstance(ThreesideTextDiffViewer.class);

  @NotNull private final EditorFactory myEditorFactory = EditorFactory.getInstance();

  @NotNull protected final SimpleDiffPanel myPanel;
  @NotNull protected final ThreesideTextContentPanel myContentPanel;

  @NotNull private final List<EditorEx> myEditors;
  @NotNull private final List<? extends EditorEx> myEditableEditors;
  @NotNull private final List<TextEditorHolder> myHolders;
  @NotNull private final List<DocumentContent> myActualContents;

  @NotNull private final MyVisibleAreaListener myVisibleAreaListener1 = new MyVisibleAreaListener(Side.LEFT);
  @NotNull private final MyVisibleAreaListener myVisibleAreaListener2 = new MyVisibleAreaListener(Side.RIGHT);

  @NotNull protected final SetEditorSettingsAction myEditorSettingsAction;

  @NotNull private final ThreesideFocusTrackerSupport myFocusTrackerSupport;

  @Nullable private ThreesideSyncScrollSupport mySyncScrollSupport;

  public ThreesideTextDiffViewer(@NotNull DiffContext context, @NotNull ContentDiffRequest request) {
    super(context, request);

    List<DiffContent> contents = myRequest.getContents();
    myActualContents = ContainerUtil.newArrayList((DocumentContent)contents.get(0),
                                                  (DocumentContent)contents.get(1),
                                                  (DocumentContent)contents.get(2));


    myHolders = createEditors();
    myEditors = ContainerUtil.map(myHolders, new Function<TextEditorHolder, EditorEx>() {
      @Override
      public EditorEx fun(TextEditorHolder holder) {
        return holder.getEditor();
      }
    });


    List<JComponent> titlePanel = DiffUtil.createTextTitles(myRequest, myEditors);
    myFocusTrackerSupport = new ThreesideFocusTrackerSupport(myHolders);
    myContentPanel = new ThreesideTextContentPanel(myHolders, titlePanel);

    myPanel = new SimpleDiffPanel(myContentPanel, this, context);


    //new MyFocusOppositePaneAction().setupAction(myPanel, this); // TODO

    myEditorSettingsAction = new SetEditorSettingsAction(getTextSettings(), getEditors());
    myEditorSettingsAction.applyDefaults();

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
    destroyEditors();
    super.onDispose();
  }

  @Override
  @CalledInAwt
  protected void processContextHints() {
    super.processContextHints();
    myFocusTrackerSupport.processContextHints(myRequest, myContext);
  }

  @Override
  @CalledInAwt
  protected void updateContextHints() {
    super.updateContextHints();
    myFocusTrackerSupport.updateContextHints(myRequest, myContext);
  }

  @NotNull
  protected List<TextEditorHolder> createEditors() {
    boolean[] forceReadOnly = TextDiffViewerUtil.checkForceReadOnly(myContext, myRequest);

    List<TextEditorHolder> holders = new ArrayList<TextEditorHolder>(3);
    for (int i = 0; i < 3; i++) {
      DocumentContent content = myActualContents.get(i);
      holders.add(TextEditorHolder.create(myProject, content, forceReadOnly[i]));
    }

    holders.get(0).getEditor().setVerticalScrollbarOrientation(EditorEx.VERTICAL_SCROLLBAR_LEFT);
    ((EditorMarkupModel)holders.get(1).getEditor().getMarkupModel()).setErrorStripeVisible(false);

    if (Registry.is("diff.divider.repainting.disable.blitting")) {
      for (TextEditorHolder holder : holders) {
        holder.getEditor().getScrollPane().getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
      }
    }

    return holders;
  }

  private void destroyEditors() {
    for (TextEditorHolder holder : myHolders) {
      Disposer.dispose(holder);
    }
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
  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return getCurrentEditor().getContentComponent();
  }

  @NotNull
  public EditorEx getCurrentEditor() {
    return getEditor(getCurrentSide());
  }

  @NotNull
  public DocumentContent getCurrentContent() {
    return getCurrentSide().select(getActualContents());
  }

  @NotNull
  protected List<? extends EditorEx> getEditors() {
    return myEditors;
  }

  @NotNull
  protected List<? extends EditorEx> getEditableEditors() {
    return myEditableEditors;
  }

  @NotNull
  protected EditorEx getEditor(@NotNull ThreeSide side) {
    return side.select(myEditors);
  }

  @NotNull
  public List<DocumentContent> getActualContents() {
    return myActualContents;
  }

  @NotNull
  public ThreeSide getCurrentSide() {
    return myFocusTrackerSupport.getCurrentSide();
  }

  public void setCurrentSide(@NotNull ThreeSide side) {
    myFocusTrackerSupport.setCurrentSide(side);
  }

  //
  // Abstract
  //

  @CalledInAwt
  protected void scrollToLine(@NotNull ThreeSide side, int line) {
    Editor editor = getEditor(side);
    DiffUtil.scrollEditor(editor, line, false);
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
    EditorEx editor = getCurrentEditor();

    int offset = editor.getCaretModel().getOffset();
    return getCurrentContent().getOpenFileDescriptor(offset);
  }

  public static boolean canShowRequest(@NotNull DiffContext context, @NotNull DiffRequest request) {
    if (!(request instanceof ContentDiffRequest)) return false;

    List<DiffContent> contents = ((ContentDiffRequest)request).getContents();
    if (contents.size() != 3) return false;

    boolean canShow = true;
    boolean wantShow = false;
    for (DiffContent content : contents) {
      canShow &= canShowContent(content, context);
      wantShow |= wantShowContent(content, context);
    }
    return canShow && wantShow;
  }

  public static boolean canShowContent(@NotNull DiffContent content, @NotNull DiffContext context) {
    return TextEditorHolder.canShowContent(content, context);
  }

  public static boolean wantShowContent(@NotNull DiffContent content, @NotNull DiffContext context) {
    return TextEditorHolder.wantShowContent(content, context);
  }

  //
  // Actions
  //

  protected class ShowLeftBasePartialDiffAction extends ShowPartialDiffAction {
    public ShowLeftBasePartialDiffAction() {
      super(ThreeSide.LEFT, ThreeSide.BASE, DiffBundle.message("merge.partial.diff.action.name.0.1"), null, AllIcons.Diff.LeftDiff);
    }
  }

  protected class ShowBaseRightPartialDiffAction extends ShowPartialDiffAction {
    public ShowBaseRightPartialDiffAction() {
      super(ThreeSide.BASE, ThreeSide.RIGHT, DiffBundle.message("merge.partial.diff.action.name.1.2"), null, AllIcons.Diff.RightDiff);
    }
  }

  protected class ShowLeftRightPartialDiffAction extends ShowPartialDiffAction {
    public ShowLeftRightPartialDiffAction() {
      super(ThreeSide.LEFT, ThreeSide.RIGHT, DiffBundle.message("merge.partial.diff.action.name"), null, AllIcons.Diff.BranchDiff);
    }
  }

  protected class ShowPartialDiffAction extends DumbAwareAction {
    @NotNull private final ThreeSide mySide1;
    @NotNull private final ThreeSide mySide2;

    public ShowPartialDiffAction(@NotNull ThreeSide side1, @NotNull ThreeSide side2,
                                 @NotNull String text, @Nullable String description, @NotNull Icon icon) {
      super(text, description, icon);
      mySide1 = side1;
      mySide2 = side2;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      List<DiffContent> contents = myRequest.getContents();
      List<String> titles = myRequest.getContentTitles();

      DiffRequest request = new SimpleDiffRequest(myRequest.getTitle(),
                                                  mySide1.select(contents), mySide2.select(contents),
                                                  mySide1.select(titles), mySide2.select(titles));
      DiffManager.getInstance().showDiff(myProject, request, new DiffDialogHints(null, myPanel));
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
    else if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) {
      return DiffUtil.getVirtualFile(myRequest, getCurrentSide());
    }
    else if (DiffDataKeys.CURRENT_CONTENT.is(dataId)) {
      return getCurrentContent();
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
      ThreeSide side = myScrollToLine.first;
      Integer line = myScrollToLine.second;
      if (getEditor(side) == null) return false;

      scrollToLine(side, line);
      return true;
    }
  }
}
