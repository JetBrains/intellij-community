// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hint;

import com.intellij.ide.IdeTooltip;
import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.HintHint;
import com.intellij.ui.HintListener;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.ListenerUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.BitUtil;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.EDT;
import com.intellij.util.ui.TimerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.EventObject;
import java.util.List;

@ApiStatus.Internal
public final class LocalHintManager implements ClientHintManager {
  private static final Logger LOG = Logger.getInstance(LocalHintManager.class);

  private final EditorMouseListener myEditorMouseListener;
  private final DocumentListener myEditorDocumentListener;
  private final VisibleAreaListener myVisibleAreaListener;
  private final CaretListener myCaretMoveListener;
  private final SelectionListener mySelectionListener;

  private LightweightHint myQuestionHint;
  private QuestionAction myQuestionAction;

  private final List<HintManagerImpl.HintInfo> myHintsStack = ContainerUtil.createLockFreeCopyOnWriteList();
  private Editor myLastEditor;
  private boolean myRequestFocusForNextHint;

  @Override
  public boolean canShowQuestionAction(QuestionAction action) {
    ThreadingAssertions.assertEventDispatchThread();
    return myQuestionAction == null || HintManagerImpl.getPriority(myQuestionAction) <= HintManagerImpl.getPriority(action);
  }

  public LocalHintManager() {
    myCaretMoveListener = new CaretListener() {
      @Override
      public void caretPositionChanged(@NotNull CaretEvent e) {
        hideHints(HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_CARET_MOVE, false, false);
      }
    };

    mySelectionListener = new SelectionListener() {
      @Override
      public void selectionChanged(@NotNull SelectionEvent e) {
        hideHints(HintManager.HIDE_BY_CARET_MOVE, false, false);
      }
    };

    MessageBusConnection busConnection = ApplicationManager.getApplication().getMessageBus().connect();
    busConnection.subscribe(AnActionListener.TOPIC, new MyAnActionListener());
    busConnection.subscribe(DynamicPluginListener.TOPIC, new MyDynamicPluginListener());

    myEditorMouseListener = new EditorMouseListener() {
      @Override
      public void mousePressed(@NotNull EditorMouseEvent event) {
        hideAllHints();
      }
    };

    myVisibleAreaListener = e -> {
      updateScrollableHints(e);
      if (e.getOldRectangle() == null ||
          e.getOldRectangle().x != e.getNewRectangle().x ||
          e.getOldRectangle().y != e.getNewRectangle().y) {
        hideHints(HintManager.HIDE_BY_SCROLLING, false, false);
      }
    };

    myEditorDocumentListener = new BulkAwareDocumentListener() {
      @Override
      public void documentChangedNonBulk(@NotNull DocumentEvent event) {
        if (event.getOldLength() != 0 || event.getNewLength() != 0) onDocumentChange();
      }

      @Override
      public void bulkUpdateFinished(@NotNull Document document) {
        onDocumentChange();
      }
    };
  }

  private void onDocumentChange() {
    EDT.assertIsEdt();
    for (HintManagerImpl.HintInfo info : myHintsStack) {
      if (BitUtil.isSet(info.flags(), HintManager.HIDE_BY_TEXT_CHANGE)) {
        info.hint().hide();
        myHintsStack.remove(info);
      }
    }

    if (myHintsStack.isEmpty()) {
      updateLastEditor(null);
    }
  }

  /**
   * Sets whether the next {@code showXxx} call will request the focus to the
   * newly shown tooltip.
   * Note the flag applies only to the next call, i.e., is
   * reset to {@code false} after any {@code showXxx} is called.
   *
   * <p>Note: This method was created to avoid the code churn associated with
   * creating an overload to every {@code showXxx} method with an additional
   * {@code boolean requestFocus} parameter </p>
   */
  @Override
  public void setRequestFocusForNextHint(boolean requestFocus) {
    myRequestFocusForNextHint = requestFocus;
  }

  @Override
  public boolean performCurrentQuestionAction() {
    ThreadingAssertions.assertEventDispatchThread();
    if (myQuestionAction != null && myQuestionHint != null) {
      if (myQuestionHint.isVisible()) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("performing an action:" + myQuestionAction);
        }
        if (myQuestionAction.execute()) {
          if (myQuestionHint != null) {
            myQuestionHint.hide();
          }
        }
        return true;
      }

      myQuestionAction = null;
      myQuestionHint = null;
    }

    return false;
  }


  private void updateScrollableHints(VisibleAreaEvent e) {
    EDT.assertIsEdt();
    for (HintManagerImpl.HintInfo info : myHintsStack) {
      if (info.hint() != null && BitUtil.isSet(info.flags(), HintManager.UPDATE_BY_SCROLLING)) {
        HintManagerImpl.updateScrollableHintPosition(e, info.hint(), BitUtil.isSet(info.flags(), HintManager.HIDE_IF_OUT_OF_EDITOR));
      }
    }
  }

  @Override
  public boolean hasShownHintsThatWillHideByOtherHint(boolean willShowTooltip) {
    EDT.assertIsEdt();
    for (HintManagerImpl.HintInfo hintInfo : myHintsStack) {
      if (hintInfo.hint().isVisible() && BitUtil.isSet(hintInfo.flags(), HintManager.HIDE_BY_OTHER_HINT)) return true;
      if (willShowTooltip && hintInfo.hint().isAwtTooltip()) {
        // only one AWT tooltip can be visible, so this hint will hide even though it's not marked with HIDE_BY_OTHER_HINT
        return true;
      }
    }
    return false;
  }

  @Override
  public void showGutterHint(@NotNull LightweightHint hint,
                             @NotNull Editor editor,
                             @NotNull HintHint hintInfo,
                             int lineNumber,
                             int horizontalOffset,
                             int flags,
                             int timeout,
                             boolean reviveOnEditorChange,
                             @Nullable Runnable onHintHidden) {
    Point point = HintManagerImpl.getHintPosition(hint, editor, new LogicalPosition(lineNumber, 0), HintManager.UNDER);
    EditorGutterComponentEx gutterComponent = (EditorGutterComponentEx)editor.getGutter();
    final JRootPane rootPane = gutterComponent.getRootPane();
    if (rootPane != null) {
      JLayeredPane layeredPane = rootPane.getLayeredPane();
      point.x = SwingUtilities.convertPoint(gutterComponent, horizontalOffset, point.y, layeredPane).x;
    }

    showEditorHint(hint, editor, hintInfo, point, flags, timeout, reviveOnEditorChange, onHintHidden);
  }

  @Override
  public void showEditorHint(@NotNull LightweightHint hint,
                             @NotNull Editor editor,
                             @NotNull HintHint hintInfo,
                             @NotNull Point p,
                             @HintManager.HideFlags int flags,
                             int timeout,
                             boolean reviveOnEditorChange,
                             @Nullable Runnable onHintHidden) {
    EDT.assertIsEdt();

    hideHints(HintManager.HIDE_BY_OTHER_HINT, false, false);

    if (editor != myLastEditor) {
      hideAllHints();
    }

    if (!ApplicationManager.getApplication().isUnitTestMode() && !editor.getContentComponent().isShowing()) return;
    if (!ApplicationManager.getApplication().isActive()) return;

    updateLastEditor(editor);

    HintManagerImpl.getPublisher().hintShown(editor, hint, flags, hintInfo);

    Component component = hint.getComponent();

    // Set focus to control so that screen readers will announce the tooltip contents.
    // Users can press "ESC" to return to the editor.
    if (myRequestFocusForNextHint) {
      hintInfo.setRequestFocus(true);
      myRequestFocusForNextHint = false;
    }
    HintManagerImpl.doShowInGivenLocation(hint, editor, p, hintInfo, true);

    if (BitUtil.isSet(flags, HintManager.HIDE_BY_MOUSEOVER)) {
      ListenerUtil.addMouseMotionListener(component, new MouseMotionAdapter() {
        @Override
        public void mouseMoved(MouseEvent e) {
          hideHints(HintManager.HIDE_BY_MOUSEOVER, true, false);
        }
      });
    }

    myHintsStack.add(new HintManagerImpl.HintInfo(hint, flags, reviveOnEditorChange));
    if (timeout > 0) {
      Timer timer = TimerUtil.createNamedTimer("Hint timeout", timeout, event -> hint.hide());
      timer.setRepeats(false);
      timer.start();
    }
  }

  @Override
  public void showHint(final @NotNull JComponent component, @NotNull RelativePoint p, int flags, int timeout, @Nullable Runnable onHintHidden) {
    EDT.assertIsEdt();

    hideHints(HintManager.HIDE_BY_OTHER_HINT, false, false);

    ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(component, null)
      .setRequestFocus(false)
      .setResizable(false)
      .setMovable(false);
    if(onHintHidden != null)
      builder.setCancelCallback(()->{
        onHintHidden.run();
        return true;
      });

    final JBPopup popup = builder.createPopup();
    popup.show(p);

    final HintManagerImpl.HintInfo info = new HintManagerImpl.HintInfo(new LightweightHint(component) {
      @Override
      public void hide() {
        popup.cancel();
      }
    }, flags, false);
    myHintsStack.add(info);
    if (timeout > 0) {
      Timer timer = TimerUtil.createNamedTimer("Popup timeout", timeout, event -> Disposer.dispose(popup));
      timer.setRepeats(false);
      timer.start();
    }
  }

  @Override
  public void hideAllHints() {
    EDT.assertIsEdt();
    for (HintManagerImpl.HintInfo info : myHintsStack) {
      if (!info.hint().vetoesHiding()) {
        info.hint().hide();
      }
    }
    cleanup();
  }

  @Override
  public void cleanup() {
    myHintsStack.clear();
    updateLastEditor(null);
  }

  /**
   * @return coordinates in a layered pane coordinate system.
   */
  @Override
  public Point getHintPosition(@NotNull LightweightHint hint, @NotNull Editor editor, @HintManager.PositionFlags short constraint) {
    EDT.assertIsEdt();
    LogicalPosition pos = editor.getCaretModel().getLogicalPosition();
    final DataContext dataContext = ((EditorEx)editor).getDataContext();
    final Rectangle dominantArea = PlatformDataKeys.DOMINANT_HINT_AREA_RECTANGLE.getData(dataContext);

    if (dominantArea != null) {
      return HintManagerImpl.getHintPositionRelativeTo(hint, editor, constraint, dominantArea, pos);
    }

    JRootPane rootPane = editor.getComponent().getRootPane();
    if (rootPane != null) {
      JLayeredPane lp = rootPane.getLayeredPane();
      for (HintManagerImpl.HintInfo info : myHintsStack) {
        if (!info.hint().isSelectingHint()) continue;
        IdeTooltip tooltip = info.hint().getCurrentIdeTooltip();
        if (tooltip != null) {
          Point p = tooltip.getShowingPoint().getPoint(lp);
          if (info.hint() != hint) {
            switch (constraint) {
              case HintManager.ABOVE -> {
                if (tooltip.getPreferredPosition() == Balloon.Position.below) {
                  p.y -= tooltip.getPositionChangeY();
                }
              }
              case HintManager.UNDER, HintManager.RIGHT_UNDER -> {
                if (tooltip.getPreferredPosition() == Balloon.Position.above) {
                  p.y += tooltip.getPositionChangeY();
                }
              }
              case HintManager.RIGHT -> {
                if (tooltip.getPreferredPosition() == Balloon.Position.atLeft) {
                  p.x += tooltip.getPositionChangeX();
                }
              }
              case HintManager.LEFT -> {
                if (tooltip.getPreferredPosition() == Balloon.Position.atRight) {
                  p.x -= tooltip.getPositionChangeX();
                }
              }
            }
          }
          return p;
        }

        Rectangle rectangle = info.hint().getBounds();
        JComponent c = info.hint().getComponent();
        rectangle = SwingUtilities.convertRectangle(c.getParent(), rectangle, lp);

        return HintManagerImpl.getHintPositionRelativeTo(hint, editor, constraint, rectangle, pos);
      }
    }

    return HintManagerImpl.getHintPosition(hint, editor, pos, constraint);
  }

  @Override
  public void showQuestionHint(final @NotNull Editor editor,
                               final @NotNull Point p,
                               final int offset1,
                               final int offset2,
                               final @NotNull LightweightHint hint,
                               int flags,
                               final @NotNull QuestionAction action,
                               @HintManager.PositionFlags short constraint) {
    ThreadingAssertions.assertEventDispatchThread();
    hideQuestionHint();
    RangeHighlighter highlighter;
    if (offset1 != offset2) {
      TextAttributes attributes = new TextAttributes();
      attributes.setEffectColor(HintUtil.QUESTION_UNDERSCORE_COLOR);
      attributes.setEffectType(EffectType.LINE_UNDERSCORE);
      highlighter = editor.getMarkupModel()
        .addRangeHighlighter(offset1, offset2, HighlighterLayer.ERROR + 1, attributes, HighlighterTargetArea.EXACT_RANGE);
    }
    else {
      highlighter = null;
    }

    hint.addHintListener(new HintListener() {
      @Override
      public void hintHidden(@NotNull EventObject event) {
        hint.removeHintListener(this);
        if (highlighter != null) {
          highlighter.dispose();
        }

        if (myQuestionHint == hint) {
          myQuestionAction = null;
          myQuestionHint = null;
        }
      }
    });

    showEditorHint(hint, editor, HintManagerImpl.createHintHint(editor, p, hint, constraint),
                   p, flags, 0, false, null);
    myQuestionAction = action;
    myQuestionHint = hint;
  }

  private void hideQuestionHint() {
    ThreadingAssertions.assertEventDispatchThread();
    if (myQuestionHint != null) {
      myQuestionHint.hide();
      myQuestionHint = null;
      myQuestionAction = null;
    }
  }

  private void updateLastEditor(final Editor editor) {
    if (myLastEditor != editor) {
      if (myLastEditor != null) {
        myLastEditor.removeEditorMouseListener(myEditorMouseListener);
        myLastEditor.getDocument().removeDocumentListener(myEditorDocumentListener);
        myLastEditor.getScrollingModel().removeVisibleAreaListener(myVisibleAreaListener);
        myLastEditor.getCaretModel().removeCaretListener(myCaretMoveListener);
        myLastEditor.getSelectionModel().removeSelectionListener(mySelectionListener);
      }

      myLastEditor = editor;
      if (myLastEditor != null) {
        myLastEditor.addEditorMouseListener(myEditorMouseListener);
        myLastEditor.getDocument().addDocumentListener(myEditorDocumentListener);
        myLastEditor.getScrollingModel().addVisibleAreaListener(myVisibleAreaListener);
        myLastEditor.getCaretModel().addCaretListener(myCaretMoveListener);
        myLastEditor.getSelectionModel().addSelectionListener(mySelectionListener);
      }
    }
  }

  private final class MyAnActionListener implements AnActionListener {
    @Override
    public void beforeActionPerformed(@NotNull AnAction action, @NotNull AnActionEvent event) {
      if (HintManagerImpl.isActionToIgnore(action)) return;

      AnAction escapeAction = ActionManagerEx.getInstanceEx().getAction(IdeActions.ACTION_EDITOR_ESCAPE);
      if (action == escapeAction) return;

      hideHints(HintManager.HIDE_BY_ANY_KEY, false, false);
    }
  }

  private final class MyDynamicPluginListener implements DynamicPluginListener {
    @Override
    public void pluginUnloaded(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
      cleanup();
    }
  }

  @Override
  public boolean isEscapeHandlerEnabled() {
    boolean isEDT = EDT.isCurrentThreadEdt();
    HintManagerImpl.HintInfo[] arr = myHintsStack.toArray(new HintManagerImpl.HintInfo[0]);
    for (int i = arr.length - 1; i > -1; i--) {
      HintManagerImpl.HintInfo info = arr[i];
      if (!info.hint().isVisible()) {
        if (isEDT) {
          myHintsStack.remove(info);
          info.hint().hide();
        }
        continue;
      }

      if ((info.flags() & (HintManager.HIDE_BY_ESCAPE | HintManager.HIDE_BY_ANY_KEY)) != 0) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean hideHints(int mask, boolean onlyOne, boolean editorChanged) {
    EDT.assertIsEdt();
    boolean result = false;
    HintManagerImpl.HintInfo[] arr = myHintsStack.toArray(new HintManagerImpl.HintInfo[0]);
    for (int i = arr.length - 1; i > -1; i--) {
      HintManagerImpl.HintInfo info = arr[i];
      if (!info.hint().isVisible() && !info.hint().vetoesHiding()) {
        myHintsStack.remove(info);
        info.hint().hide();
        continue;
      }

      if ((info.flags() & mask) != 0 || editorChanged && !info.reviveOnEditorChange()) {
        myHintsStack.remove(info);
        info.hint().hide();
        if ((mask & HintManager.HIDE_BY_ESCAPE) == 0 || (info.flags() & HintManager.DONT_CONSUME_ESCAPE) == 0) {
          result = true;
          if (onlyOne) {
            break;
          }
        }
      }
    }
    if (myHintsStack.isEmpty()) {
      updateLastEditor(null);
    }
    return result;
  }

  @Override
  public void onProjectClosed(@NotNull Project project) {
    myQuestionAction = null;
    myQuestionHint = null;
    if (myLastEditor != null && project == myLastEditor.getProject()) {
      updateLastEditor(null);
    }
  }
}
