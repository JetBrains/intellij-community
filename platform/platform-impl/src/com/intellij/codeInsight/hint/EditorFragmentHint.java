// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hint;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.reference.SoftReference;
import com.intellij.ui.HintHint;
import com.intellij.ui.HintListener;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import java.awt.Color;
import java.awt.Point;
import java.lang.ref.WeakReference;
import java.util.EventObject;

final class EditorFragmentHint extends LightweightHint {
  private static final Key<WeakReference<LightweightHint>> CURRENT_HINT = Key.create("EditorFragmentComponent.currentHint");
  private static final int LINE_BORDER_THICKNESS = 1;
  private static final int EMPTY_BORDER_THICKNESS = 2;

  static @Nullable LightweightHint show(
    Editor editor,
    TextRange range,
    boolean showFolding,
    boolean hideByAnyKey
  ) {
    if (!(editor instanceof EditorEx editorEx)) {
      return null;
    }
    JRootPane rootPane = editor.getComponent().getRootPane();
    if (rootPane == null) {
      return null;
    }
    JLayeredPane layeredPane = rootPane.getLayeredPane();
    int lineHeight = editor.getLineHeight();
    int overhang = editor.getScrollingModel().getVisibleArea().y -
                   editor.logicalPositionToXY(editor.offsetToLogicalPosition(range.getEndOffset())).y;
    int yRelative = (0 < overhang && overhang < lineHeight)
                    ? lineHeight - overhang + JBUIScale.scale(LINE_BORDER_THICKNESS + EMPTY_BORDER_THICKNESS)
                    : 0;
    JViewport viewport = editorEx.getScrollPane().getViewport();
    Point point = SwingUtilities.convertPoint(viewport, -2, yRelative, layeredPane);
    return showAt(
      editor,
      range,
      point.y,
      /* showUpward */true,
      showFolding,
      hideByAnyKey,
      /* useCaretRowBackground */ false
    );
  }

  static @Nullable LightweightHint showAt(
    Editor editor,
    TextRange range,
    int y,
    boolean showUpward,
    boolean showFolding,
    boolean hideByAnyKey,
    boolean useCaretRowBackground
  ) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return null;
    }
    Document document = editor.getDocument();
    int startLine = getLine(range, document);
    int endLine = document.getLineNumber(range.getEndOffset()) + 1;
    if (startLine >= endLine) {
      return null;
    }
    EditorFragmentComponent fragmentComponent = EditorFragmentComponent.createEditorFragmentComponent(
      editor,
      startLine,
      endLine,
      showFolding,
      true,
      useCaretRowBackground
    );
    if (showUpward) {
      y -= fragmentComponent.getPreferredSize().height;
      y = Math.max(0, y);
    }
    JComponent c = editor.getComponent();
    int x = SwingUtilities.convertPoint( //IDEA-68016
      c,
      new Point(JBUIScale.scale(-3), 0),
      UIUtil.getRootPane(c)
    ).x;
    Point point = new Point(x, y);
    LightweightHint currentHint = SoftReference.dereference(editor.getUserData(CURRENT_HINT));
    if (currentHint != null) {
      currentHint.hide();
    }
    LightweightHint hint = new EditorFragmentHint(fragmentComponent);
    int flags = (hideByAnyKey ? HintManager.HIDE_BY_ANY_KEY : 0) |
                HintManager.HIDE_BY_SCROLLING |
                HintManager.HIDE_BY_TEXT_CHANGE |
                HintManager.HIDE_BY_MOUSEOVER;
    HintManagerImpl.getInstanceImpl().showEditorHint(
      hint,
      editor,
      point,
      flags,
      0,
      false,
      new HintHint(editor, point)
    );
    editor.putUserData(CURRENT_HINT, new WeakReference<>(hint));
    return hint;
  }

  static @NotNull CompoundBorder createBorder(@NotNull Editor editor) {
    Color borderColor = editor.getColorsScheme().getColor(EditorColors.SELECTED_TEARLINE_COLOR);
    Border outsideBorder = JBUI.Borders.customLine(borderColor, LINE_BORDER_THICKNESS);
    Border insideBorder = JBUI.Borders.empty(EMPTY_BORDER_THICKNESS);
    return BorderFactory.createCompoundBorder(outsideBorder, insideBorder);
  }

  EditorFragmentHint(@NotNull EditorFragmentComponent fragmentComponent) {
    super(fragmentComponent);
    setForceLightweightPopup(true);
    // Use the hidden event as the lifecycle boundary. This hint is forced to layered-pane mode, so the usual popup
    // cancellation hook is not used; some paths, for example ESC, hide it without calling hide(boolean).
    addHintListener(new HintListener() {
      @Override
      public void hintHidden(@NotNull EventObject event) {
        EditorFragmentComponent fragmentComponent1 = (EditorFragmentComponent) getComponent();
        fragmentComponent1.releaseImages();
      }
    });
  }

  @Override
  public void hide() {
    // needed for Alt-Q multiple times
    // Q: not good?
    SwingUtilities.invokeLater(() -> hide(false));
  }

  private static int getLine(@NotNull TextRange range, @NotNull Document document) {
    int startOffset = range.getStartOffset();
    int startLine = document.getLineNumber(startOffset);
    CharSequence text = document.getImmutableCharSequence();
    // There is a possible case that we have a situation like below:
    //    line 1
    //    line 2 <fragment start>
    //    line 3<fragment end>
    // We don't want to include 'line 2' to the target fragment then.
    boolean incrementLine = false;
    for (int offset = startOffset, max = Math.min(range.getEndOffset(), text.length()); offset < max; offset++) {
      char c = text.charAt(offset);
      incrementLine = StringUtil.isWhiteSpace(c);
      if (!incrementLine || c == '\n') {
        break;
      }
    }
    if (incrementLine) {
      startLine++;
    }
    return startLine;
  }
}
