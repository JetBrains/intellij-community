package com.intellij.openapi.util.diff.tools.util.twoside;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffNavigationContext;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.diff.actions.impl.FocusOppositePaneAction;
import com.intellij.openapi.util.diff.actions.impl.OpenInEditorWithMouseAction;
import com.intellij.openapi.util.diff.api.FrameDiffTool.DiffContext;
import com.intellij.openapi.util.diff.contents.DiffContent;
import com.intellij.openapi.util.diff.contents.DocumentContent;
import com.intellij.openapi.util.diff.contents.EmptyContent;
import com.intellij.openapi.util.diff.requests.ContentDiffRequest;
import com.intellij.openapi.util.diff.requests.DiffRequest;
import com.intellij.openapi.util.diff.tools.util.DiffUserDataKeys;
import com.intellij.openapi.util.diff.tools.util.DiffUserDataKeys.ScrollToPolicy;
import com.intellij.openapi.util.diff.tools.util.SyncScrollSupport;
import com.intellij.openapi.util.diff.tools.util.base.TextDiffViewerBase;
import com.intellij.openapi.util.diff.util.CalledInAwt;
import com.intellij.openapi.util.diff.util.DiffUtil;
import com.intellij.openapi.util.diff.util.Side;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.Collections;
import java.util.List;

public abstract class TwosideTextDiffViewer extends TextDiffViewerBase {
  public static final Logger LOG = Logger.getInstance(TwosideTextDiffViewer.class);

  @NotNull private final EditorFactory myEditorFactory = EditorFactory.getInstance();

  @NotNull protected final TwosideTextDiffPanel myPanel;
  @NotNull protected final TwosideTextContentPanel myContentPanel;

  @Nullable protected final EditorEx myEditor1;
  @Nullable protected final EditorEx myEditor2;

  @Nullable protected final DocumentContent myActualContent1;
  @Nullable protected final DocumentContent myActualContent2;

  @NotNull protected final MySetEditorSettingsAction myEditorSettingsAction;

  @NotNull private final MyEditorFocusListener myEditorFocusListener1 = new MyEditorFocusListener(Side.LEFT);
  @NotNull private final MyEditorFocusListener myEditorFocusListener2 = new MyEditorFocusListener(Side.RIGHT);
  @NotNull private final MyVisibleAreaListener myVisibleAreaListener = new MyVisibleAreaListener();

  @NotNull private final MyScrollToLineHelper myScrollToLineHelper = new MyScrollToLineHelper();

  @Nullable private SyncScrollSupport mySyncScrollListener;

  @NotNull private Side myCurrentSide;

  public TwosideTextDiffViewer(@NotNull DiffContext context, @NotNull ContentDiffRequest request) {
    super(context, request);

    DiffContent[] contents = myRequest.getContents();
    myActualContent1 = contents[0] instanceof DocumentContent ? ((DocumentContent)contents[0]) : null;
    myActualContent2 = contents[1] instanceof DocumentContent ? ((DocumentContent)contents[1]) : null;
    assert myActualContent1 != null || myActualContent2 != null;


    List<JComponent> titlePanel = DiffUtil.createTextTitles(myRequest);
    List<EditorEx> editors = createEditors();

    myEditor1 = editors.get(0);
    myEditor2 = editors.get(1);
    assert myEditor1 != null || myEditor2 != null;

    myCurrentSide = myEditor1 == null ? Side.RIGHT : Side.LEFT;

    myContentPanel = new TwosideTextContentPanel(titlePanel, myEditor1, myEditor2);

    myPanel = new TwosideTextDiffPanel(this, myContentPanel, this, context);


    new MyFocusOppositePaneAction().setupAction(myPanel, this);

    myEditorSettingsAction = new MySetEditorSettingsAction(getTextSettings());
    myEditorSettingsAction.applyDefaults();

    new MyOpenInEditorWithMouseAction().register(getEditors());
  }

  @Override
  protected void onInit() {
    super.onInit();
    processContextHints();
  }

  @Override
  protected void onDispose() {
    super.onDispose();
    updateContextHints();
    destroyEditors();
  }

  private void processContextHints() {
    if (myEditor1 == null) {
      myCurrentSide = Side.RIGHT;
    }
    else if (myEditor2 == null) {
      myCurrentSide = Side.LEFT;
    }
    else {
      Side side = myContext.getUserData(DiffUserDataKeys.PREFERRED_FOCUS_SIDE);
      if (side != null) myCurrentSide = side;
    }

    myScrollToLineHelper.processContext();
    myScrollToLineHelper.onInit();
  }

  private void updateContextHints() {
    if (myEditor1 != null && myEditor2 != null) {
      myContext.putUserData(DiffUserDataKeys.PREFERRED_FOCUS_SIDE, myCurrentSide);
    }

    myScrollToLineHelper.updateContext();
  }

  @NotNull
  protected List<EditorEx> createEditors() {
    EditorEx editor1 = null;
    EditorEx editor2 = null;
    if (myActualContent1 != null) {
      editor1 = DiffUtil.createEditor(myActualContent1.getDocument(), myProject, false);
      DiffUtil.configureEditor(editor1, myActualContent1, myProject);
    }
    if (myActualContent2 != null) {
      editor2 = DiffUtil.createEditor(myActualContent2.getDocument(), myProject, false);
      DiffUtil.configureEditor(editor2, myActualContent2, myProject);
    }
    if (editor1 != null && editor2 != null) {
      editor1.setVerticalScrollbarOrientation(EditorEx.VERTICAL_SCROLLBAR_LEFT);
    }
    return ContainerUtil.newArrayList(editor1, editor2);
  }

  //
  // Diff
  //

  @Override
  protected void onDocumentChange(@NotNull DocumentEvent event) {
    super.onDocumentChange(event);
    myContentPanel.repaintDivider();
  }

  @CalledInAwt
  protected void scrollOnRediff() {
    myScrollToLineHelper.onRediff();
  }

  //
  // Listeners
  //

  private void destroyEditors() {
    if (myEditor1 != null) myEditorFactory.releaseEditor(myEditor1);
    if (myEditor2 != null) myEditorFactory.releaseEditor(myEditor2);
  }

  @CalledInAwt
  @Override
  protected void installEditorListeners() {
    super.installEditorListeners();
    if (myEditor1 != null) {
      myEditor1.getContentComponent().addFocusListener(myEditorFocusListener1);
      myEditor1.getScrollingModel().addVisibleAreaListener(myVisibleAreaListener);
    }
    if (myEditor2 != null) {
      myEditor2.getContentComponent().addFocusListener(myEditorFocusListener2);
      myEditor2.getScrollingModel().addVisibleAreaListener(myVisibleAreaListener);
    }
    if (myEditor1 != null && myEditor2 != null) {
      if (getSyncScrollable() != null) {
        mySyncScrollListener = new SyncScrollSupport(myEditor1, myEditor2, getSyncScrollable());
      }
    }
  }

  @CalledInAwt
  @Override
  protected void destroyEditorListeners() {
    super.destroyEditorListeners();
    if (myEditor1 != null) {
      myEditor1.getContentComponent().removeFocusListener(myEditorFocusListener1);
      myEditor1.getScrollingModel().removeVisibleAreaListener(myVisibleAreaListener);
    }
    if (myEditor2 != null) {
      myEditor2.getContentComponent().removeFocusListener(myEditorFocusListener2);
      myEditor2.getScrollingModel().removeVisibleAreaListener(myVisibleAreaListener);
    }
    if (myEditor1 != null && myEditor2 != null) {
      if (mySyncScrollListener != null) {
        mySyncScrollListener = null;
      }
    }
  }

  //
  // Getters
  //

  @NotNull
  @Override
  protected List<? extends EditorEx> getEditors() {
    if (myEditor1 != null && myEditor2 != null) {
      return ContainerUtil.list(myEditor1, myEditor2);
    }
    if (myEditor1 != null) {
      return Collections.singletonList(myEditor1);
    }
    if (myEditor2 != null) {
      return Collections.singletonList(myEditor2);
    }
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myPanel.getPreferredFocusedComponent();
  }

  @NotNull
  public Side getCurrentSide() {
    return myCurrentSide;
  }

  @NotNull
  public EditorEx getCurrentEditor() {
    //noinspection ConstantConditions
    return getCurrentSide().isLeft() ? myEditor1 : myEditor2;
  }

  @Nullable
  protected EditorEx getEditor1() {
    return myEditor1;
  }

  @Nullable
  protected EditorEx getEditor2() {
    return myEditor2;
  }

  //
  // Abstract
  //

  @CalledInAwt
  protected void scrollToLine(@NotNull Side side, int line) {
    Editor editor = side.select(myEditor1, myEditor2);
    if (editor == null) return;
    DiffUtil.scrollEditor(editor, line);
    myCurrentSide = side;
  }

  @CalledInAwt
  protected boolean doScrollToChange(@NotNull ScrollToPolicy scrollToChangePolicy) {
    return false;
  }

  @CalledInAwt
  protected boolean doScrollToContext(@NotNull DiffNavigationContext context) {
    return false;
  }

  @Nullable
  protected abstract SyncScrollSupport.SyncScrollable getSyncScrollable();

  //
  // Misc
  //

  @Override
  protected boolean tryRediffSynchronously() {
    return myPanel.isWindowFocused();
  }

  @Nullable
  @Override
  protected OpenFileDescriptor getOpenFileDescriptor() {
    EditorEx editor = getCurrentEditor();

    DocumentContent content = getCurrentSide().select(myActualContent1, myActualContent2);
    if (content == null) return null;

    int offset = editor.getCaretModel().getOffset();
    return content.getOpenFileDescriptor(offset);
  }

  public static boolean canShowRequest(@NotNull DiffContext context, @NotNull DiffRequest request) {
    if (!(request instanceof ContentDiffRequest)) return false;

    DiffContent[] contents = ((ContentDiffRequest)request).getContents();
    if (contents.length != 2) return false;

    if (!canShowContent(contents[0])) return false;
    if (!canShowContent(contents[1])) return false;

    if (contents[0] instanceof EmptyContent && contents[1] instanceof EmptyContent) return false;

    return true;
  }

  public static boolean canShowContent(@NotNull DiffContent content) {
    if (content instanceof EmptyContent) return true;
    if (content instanceof DocumentContent) return true;
    return false;
  }

  //
  // Actions
  //

  private class MyFocusOppositePaneAction extends FocusOppositePaneAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      if (myEditor1 == null || myEditor2 == null) return;

      myCurrentSide = myCurrentSide.other();
      myPanel.requestFocus();
      getCurrentEditor().getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    }
  }

  private class MyOpenInEditorWithMouseAction extends OpenInEditorWithMouseAction {
    @Override
    protected OpenFileDescriptor getDescriptor(@NotNull Editor editor, int line) {
      if (editor != myEditor1 && editor != myEditor2) return null;
      Side side = Side.fromLeft(editor == myEditor1);

      DocumentContent content = side.select(myActualContent1, myActualContent2);
      if (content == null) return null;

      int offset = editor.logicalPositionToOffset(new LogicalPosition(line, 0));

      return content.getOpenFileDescriptor(offset);
    }
  }

  //
  // Helpers
  //

  @NotNull
  protected Graphics2D getDividerGraphics(@NotNull Graphics g, @NotNull Component divider) {
    assert myEditor1 != null && myEditor2 != null;

    int width = divider.getWidth();
    int editorHeight = myEditor1.getComponent().getHeight();
    int dividerOffset = divider.getLocationOnScreen().y;
    int editorOffset = myEditor1.getComponent().getLocationOnScreen().y;
    Graphics2D gg = (Graphics2D)g.create(0, editorOffset - dividerOffset, width, editorHeight);
    gg.translate(0, -1);

    return gg;
  }

  private class MyEditorFocusListener extends FocusAdapter {
    @NotNull private final Side mySide;

    private MyEditorFocusListener(@NotNull Side side) {
      mySide = side;
    }

    public void focusGained(FocusEvent e) {
      if (myEditor1 == null || myEditor2 == null) return;
      myCurrentSide = mySide;
    }
  }

  private class MyVisibleAreaListener implements VisibleAreaListener {
    @Override
    public void visibleAreaChanged(VisibleAreaEvent e) {
      if (mySyncScrollListener != null) mySyncScrollListener.visibleAreaChanged(e);
      myContentPanel.repaintDivider();
    }
  }

  private class MyScrollToLineHelper {
    protected boolean myShouldScroll = true;

    @Nullable private ScrollToPolicy myScrollToChange;
    @Nullable private EditorsPosition myEditorsPosition;
    @Nullable private LogicalPosition[] myCaretPosition;
    @Nullable private DiffNavigationContext myNavigationContext;

    public void processContext() {
      myScrollToChange = myRequest.getUserData(DiffUserDataKeys.SCROLL_TO_CHANGE);
      myEditorsPosition = myRequest.getUserData(EditorsPosition.KEY);
      myCaretPosition = myRequest.getUserData(DiffUserDataKeys.EDITORS_CARET_POSITION);
      myNavigationContext = myRequest.getUserData(DiffUserDataKeys.NAVIGATION_CONTEXT);
    }

    public void updateContext() {
      LogicalPosition[] carets = new LogicalPosition[2];
      carets[0] = getPosition(myEditor1);
      carets[1] = getPosition(myEditor2);

      EditorsPosition editorsPosition = new EditorsPosition(carets, getPoint(myEditor1), getPoint(myEditor2));

      myRequest.putUserData(DiffUserDataKeys.SCROLL_TO_CHANGE, null);
      myRequest.putUserData(EditorsPosition.KEY, editorsPosition);
      myRequest.putUserData(DiffUserDataKeys.EDITORS_CARET_POSITION, carets);
      myRequest.putUserData(DiffUserDataKeys.NAVIGATION_CONTEXT, null);
    }

    public void onInit() {
      if (!myShouldScroll) return;
      if (myScrollToChange != null) return;
      if (myNavigationContext != null) return;

      if (myCaretPosition != null && myCaretPosition.length == 2) {
        if (myEditor1 != null) myEditor1.getCaretModel().moveToLogicalPosition(myCaretPosition[0]);
        if (myEditor2 != null) myEditor2.getCaretModel().moveToLogicalPosition(myCaretPosition[1]);

        if (myEditorsPosition != null && myEditorsPosition.isSame(myCaretPosition)) {
          scrollToPoint(myEditor1, myEditorsPosition.myPoint1);
          scrollToPoint(myEditor2, myEditorsPosition.myPoint2);
        }
        else {
          getCurrentEditor().getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
        }
        myShouldScroll = false;
      }
    }

    public void onRediff() {
      if (myShouldScroll && myScrollToChange != null) {
        myShouldScroll = !doScrollToChange(myScrollToChange);
      }
      if (myShouldScroll && myNavigationContext != null) {
        myShouldScroll = !doScrollToContext(myNavigationContext);
      }
      if (myShouldScroll) {
        doScrollToChange(ScrollToPolicy.FIRST_CHANGE);
      }
      myShouldScroll = false;
    }

    @NotNull
    private LogicalPosition getPosition(@Nullable Editor editor) {
      return editor != null ? editor.getCaretModel().getLogicalPosition() : new LogicalPosition(0, 0);
    }

    @NotNull
    private Point getPoint(@Nullable Editor editor) {
      if (editor == null) return new Point(0, 0);
      ScrollingModel model = editor.getScrollingModel();
      return new Point(model.getHorizontalScrollOffset(), model.getVerticalScrollOffset());
    }

    private void scrollToPoint(@Nullable Editor editor, @NotNull Point point) {
      if (editor == null) return;
      editor.getScrollingModel().disableAnimation();
      editor.getScrollingModel().scrollHorizontally(point.x);
      editor.getScrollingModel().scrollVertically(point.y);
      editor.getScrollingModel().enableAnimation();
    }
  }

  private static class EditorsPosition {
    public static final Key<EditorsPosition> KEY = Key.create("Diff.EditorsPosition");

    @NotNull public final LogicalPosition[] myCaretPosition;
    @NotNull public final Point myPoint1;
    @NotNull public final Point myPoint2;

    public EditorsPosition(@NotNull LogicalPosition[] caretPosition, @NotNull Point point1, @NotNull Point point2) {
      myCaretPosition = caretPosition;
      myPoint1 = point1;
      myPoint2 = point2;
    }

    public boolean isSame(@Nullable LogicalPosition[] caretPosition) {
      // TODO: allow small fluctuations ?
      if (caretPosition == null) return true;
      if (caretPosition.length != 2) return false;
      if (!caretPosition[0].equals(myCaretPosition[0])) return false;
      if (!caretPosition[1].equals(myCaretPosition[1])) return false;
      return true;
    }
  }
}
