package com.intellij.openapi.util.diff.tools.util.threeside;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.diff.api.FrameDiffTool.DiffContext;
import com.intellij.openapi.util.diff.contents.DiffContent;
import com.intellij.openapi.util.diff.contents.DocumentContent;
import com.intellij.openapi.util.diff.requests.ContentDiffRequest;
import com.intellij.openapi.util.diff.requests.DiffRequest;
import com.intellij.openapi.util.diff.tools.util.DiffUserDataKeys;
import com.intellij.openapi.util.diff.tools.util.DiffUserDataKeys.ScrollToPolicy;
import com.intellij.openapi.util.diff.tools.util.SyncScrollSupport;
import com.intellij.openapi.util.diff.tools.util.SyncScrollSupport.ThreesideSyncScrollSupport;
import com.intellij.openapi.util.diff.tools.util.base.TextDiffViewerBase;
import com.intellij.openapi.util.diff.util.CalledInAwt;
import com.intellij.openapi.util.diff.util.DiffUtil;
import com.intellij.openapi.util.diff.util.Side;
import com.intellij.openapi.util.diff.util.ThreeSide;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.List;

public abstract class ThreesideTextDiffViewer extends TextDiffViewerBase {
  public static final Logger LOG = Logger.getInstance(ThreesideTextDiffViewer.class);

  @NotNull private final EditorFactory myEditorFactory = EditorFactory.getInstance();

  @NotNull protected final ThreesideTextDiffPanel myPanel;
  @NotNull protected final ThreesideTextContentPanel myContentPanel;

  @NotNull protected final List<EditorEx> myEditors;

  @NotNull protected final List<DocumentContent> myActualContents;

  @NotNull private final List<MyEditorFocusListener> myEditorFocusListeners =
    ContainerUtil.newArrayList(new MyEditorFocusListener(ThreeSide.LEFT),
                               new MyEditorFocusListener(ThreeSide.BASE),
                               new MyEditorFocusListener(ThreeSide.RIGHT));
  @NotNull private final MyVisibleAreaListener myVisibleAreaListener1 = new MyVisibleAreaListener(Side.LEFT);
  @NotNull private final MyVisibleAreaListener myVisibleAreaListener2 = new MyVisibleAreaListener(Side.RIGHT);

  @NotNull protected final TextDiffViewerBase.MySetEditorSettingsAction myEditorSettingsAction;

  @NotNull private final MyScrollToLineHelper myScrollToLineHelper = new MyScrollToLineHelper();

  @Nullable private ThreesideSyncScrollSupport mySyncScrollListener;

  @NotNull private ThreeSide myCurrentSide;

  public ThreesideTextDiffViewer(@NotNull DiffContext context, @NotNull ContentDiffRequest request) {
    super(context, request);

    DiffContent[] contents = myRequest.getContents();
    myActualContents = ContainerUtil.newArrayList((DocumentContent)contents[0], (DocumentContent)contents[1], (DocumentContent)contents[2]);


    List<JComponent> titlePanel = DiffUtil.createTextTitles(myRequest);
    myEditors = createEditors();

    myCurrentSide = ThreeSide.BASE;

    myContentPanel = new ThreesideTextContentPanel(myEditors, titlePanel);

    myPanel = new ThreesideTextDiffPanel(this, myContentPanel, this, context);


    //new MyFocusOppositePaneAction().setupAction(myPanel, this); // FIXME

    myEditorSettingsAction = new MySetEditorSettingsAction(getTextSettings());
    myEditorSettingsAction.applyDefaults();
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
    ThreeSide side = myContext.getUserData(DiffUserDataKeys.PREFERRED_FOCUS_THREESIDE);
    if (side != null) myCurrentSide = side;

    myScrollToLineHelper.processContext();
    myScrollToLineHelper.onInit();
  }

  private void updateContextHints() {
    myContext.putUserData(DiffUserDataKeys.PREFERRED_FOCUS_THREESIDE, myCurrentSide);

    myScrollToLineHelper.updateContext();
  }

  @NotNull
  protected List<EditorEx> createEditors() {
    List<EditorEx> editors = new ArrayList<EditorEx>(3);

    for (DocumentContent content : myActualContents) {
      EditorEx editor = DiffUtil.createEditor(content.getDocument(), myProject, false);
      DiffUtil.configureEditor(editor, content, myProject);
      editors.add(editor);
    }

    editors.get(0).setVerticalScrollbarOrientation(EditorEx.VERTICAL_SCROLLBAR_LEFT);
    ((EditorMarkupModel)editors.get(1).getMarkupModel()).setErrorStripeVisible(false);

    return editors;
  }

  private void destroyEditors() {
    for (EditorEx editor : myEditors) {
      myEditorFactory.releaseEditor(editor);
    }
  }

  //
  // Listeners
  //

  @CalledInAwt
  @Override
  protected void installEditorListeners() {
    super.installEditorListeners();
    for (int i = 0; i < 3; i++) {
      myEditors.get(i).getContentComponent().addFocusListener(myEditorFocusListeners.get(i));
    }

    myEditors.get(0).getScrollingModel().addVisibleAreaListener(myVisibleAreaListener1);
    myEditors.get(1).getScrollingModel().addVisibleAreaListener(myVisibleAreaListener1);

    myEditors.get(1).getScrollingModel().addVisibleAreaListener(myVisibleAreaListener2);
    myEditors.get(2).getScrollingModel().addVisibleAreaListener(myVisibleAreaListener2);

    SyncScrollSupport.SyncScrollable scrollable1 = getSyncScrollable(Side.LEFT);
    SyncScrollSupport.SyncScrollable scrollable2 = getSyncScrollable(Side.RIGHT);
    if (scrollable1 != null && scrollable2 != null) {
      mySyncScrollListener = new ThreesideSyncScrollSupport(myEditors, scrollable1, scrollable2);
    }
  }

  @CalledInAwt
  @Override
  public void destroyEditorListeners() {
    super.destroyEditorListeners();

    for (int i = 0; i < 3; i++) {
      myEditors.get(i).getContentComponent().removeFocusListener(myEditorFocusListeners.get(i));
    }

    myEditors.get(0).getScrollingModel().removeVisibleAreaListener(myVisibleAreaListener1);
    myEditors.get(1).getScrollingModel().removeVisibleAreaListener(myVisibleAreaListener1);

    myEditors.get(1).getScrollingModel().removeVisibleAreaListener(myVisibleAreaListener2);
    myEditors.get(2).getScrollingModel().removeVisibleAreaListener(myVisibleAreaListener2);

    if (mySyncScrollListener != null) {
      mySyncScrollListener = null;
    }
  }

  //
  // Diff
  //

  @Override
  protected void onDocumentChange(@NotNull DocumentEvent event) {
    super.onDocumentChange(event);

    myContentPanel.repaintDividers();
  }

  @CalledInAwt
  protected void scrollOnRediff() {
    myScrollToLineHelper.onRediff();
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
    return myPanel.getPreferredFocusedComponent();
  }

  @NotNull
  public EditorEx getCurrentEditor() {
    return myCurrentSide.selectN(myEditors);
  }

  @NotNull
  @Override
  protected List<? extends EditorEx> getEditors() {
    return myEditors;
  }

  @NotNull
  public ThreeSide getCurrentSide() {
    return myCurrentSide;
  }

  //
  // Abstract
  //

  @CalledInAwt
  protected void scrollToLine(@NotNull ThreeSide side, int line) {
    Editor editor = side.selectN(myEditors);
    DiffUtil.scrollEditor(editor, line);
    myCurrentSide = side;
  }

  @CalledInAwt
  protected boolean doScrollToChange(@NotNull ScrollToPolicy scrollToChangePolicy) {
    return false;
  }

  @Nullable
  protected abstract SyncScrollSupport.SyncScrollable getSyncScrollable(@NotNull Side side);

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

    DocumentContent content = getCurrentSide().selectN(myActualContents);

    int offset = editor.getCaretModel().getOffset();
    return content.getOpenFileDescriptor(offset);
  }

  public static boolean canShowRequest(@NotNull DiffContext context, @NotNull DiffRequest request) {
    if (!(request instanceof ContentDiffRequest)) return false;

    DiffContent[] contents = ((ContentDiffRequest)request).getContents();
    if (contents.length != 3) return false;

    if (!canShowContent(contents[0])) return false;
    if (!canShowContent(contents[1])) return false;
    if (!canShowContent(contents[2])) return false;

    return true;
  }

  public static boolean canShowContent(@NotNull DiffContent content) {
    if (content instanceof DocumentContent) return true;
    return false;
  }

  //
  // Actions
  //

  //
  // Helpers
  //

  @NotNull
  protected Graphics2D getDividerGraphics(@NotNull Graphics g, @NotNull Component divider) {
    int width = divider.getWidth();
    int editorHeight = myEditors.get(0).getComponent().getHeight();
    int dividerOffset = divider.getLocationOnScreen().y;
    int editorOffset = myEditors.get(0).getComponent().getLocationOnScreen().y;
    return (Graphics2D)g.create(0, editorOffset - dividerOffset, width, editorHeight);
  }

  private class MyEditorFocusListener extends FocusAdapter {
    @NotNull private final ThreeSide mySide;

    private MyEditorFocusListener(@NotNull ThreeSide side) {
      mySide = side;
    }

    public void focusGained(FocusEvent e) {
      myCurrentSide = mySide;
    }
  }

  private class MyVisibleAreaListener implements VisibleAreaListener {
    @NotNull Side mySide;

    public MyVisibleAreaListener(@NotNull Side side) {
      mySide = side;
    }

    @Override
    public void visibleAreaChanged(VisibleAreaEvent e) {
      if (mySyncScrollListener != null) mySyncScrollListener.visibleAreaChanged(e);
      myContentPanel.repaintDivider(mySide);
    }
  }

  private class MyScrollToLineHelper {
    protected boolean myShouldScroll = true;

    @Nullable private ScrollToPolicy myScrollToChange;
    @Nullable private EditorsPosition myEditorsPosition;
    @Nullable private LogicalPosition[] myCaretPosition;

    public void processContext() {
      myScrollToChange = myRequest.getUserData(DiffUserDataKeys.SCROLL_TO_CHANGE);
      myEditorsPosition = myRequest.getUserData(EditorsPosition.KEY);
      myCaretPosition = myRequest.getUserData(DiffUserDataKeys.EDITORS_CARET_POSITION);
    }

    public void updateContext() {
      LogicalPosition[] carets = new LogicalPosition[3];
      carets[0] = getPosition(myEditors.get(0));
      carets[1] = getPosition(myEditors.get(1));
      carets[2] = getPosition(myEditors.get(2));

      Point[] points = new Point[3];
      points[0] = getPoint(myEditors.get(0));
      points[1] = getPoint(myEditors.get(1));
      points[2] = getPoint(myEditors.get(2));

      EditorsPosition editorsPosition = new EditorsPosition(carets, points);

      myRequest.putUserData(DiffUserDataKeys.SCROLL_TO_CHANGE, null);
      myRequest.putUserData(EditorsPosition.KEY, editorsPosition);
      myRequest.putUserData(DiffUserDataKeys.EDITORS_CARET_POSITION, carets);
    }

    public void onInit() {
      if (!myShouldScroll) return;
      if (myScrollToChange != null) return;

      if (myCaretPosition != null && myCaretPosition.length == 3) {
        myEditors.get(0).getCaretModel().moveToLogicalPosition(myCaretPosition[0]);
        myEditors.get(1).getCaretModel().moveToLogicalPosition(myCaretPosition[1]);
        myEditors.get(2).getCaretModel().moveToLogicalPosition(myCaretPosition[2]);

        if (myEditorsPosition != null && myEditorsPosition.isSame(myCaretPosition)) {
          scrollToPoint(myEditors.get(0), myEditorsPosition.myPoints[0]);
          scrollToPoint(myEditors.get(1), myEditorsPosition.myPoints[1]);
          scrollToPoint(myEditors.get(2), myEditorsPosition.myPoints[2]);
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
    @NotNull public final Point[] myPoints;

    public EditorsPosition(@NotNull LogicalPosition[] caretPosition, @NotNull Point[] points) {
      myCaretPosition = caretPosition;
      myPoints = points;
    }

    public boolean isSame(@Nullable LogicalPosition[] caretPosition) {
      // TODO: allow small fluctuations ?
      if (caretPosition == null) return true;
      if (caretPosition.length != 3) return false;
      if (!caretPosition[0].equals(myCaretPosition[0])) return false;
      if (!caretPosition[1].equals(myCaretPosition[1])) return false;
      if (!caretPosition[2].equals(myCaretPosition[2])) return false;
      return true;
    }
  }
}
