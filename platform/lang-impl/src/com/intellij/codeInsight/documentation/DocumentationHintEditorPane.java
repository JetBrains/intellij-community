// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.documentation;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.*;
import java.util.Map;
import java.util.function.Supplier;

final class DocumentationHintEditorPane extends DocumentationEditorPane {

  private final Project myProject;

  DocumentationHintEditorPane(
    @NotNull Project project,
    @NotNull Map<KeyStroke, ActionListener> keyboardActions,
    @NotNull Supplier<? extends @Nullable PsiElement> elementSupplier
  ) {
    super(keyboardActions, elementSupplier);
    myProject = project;
  }

  void setHint(@NotNull JBPopup hint) {
    myHint = hint;
    FocusListener focusAdapter = new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e) {
        Component previouslyFocused = WindowManagerEx.getInstanceEx().getFocusedComponent(myProject);
        if (previouslyFocused != DocumentationHintEditorPane.this) {
          hint.cancel();
        }
      }
    };
    addFocusListener(focusAdapter);
    Disposer.register(hint, () -> removeFocusListener(focusAdapter));
  }

  private JBPopup myHint; // lateinit
  private Point initialClick;

  @Override
  protected void processMouseEvent(MouseEvent e) {
    if (e.getID() == MouseEvent.MOUSE_PRESSED && myHint != null) {
      initialClick = null;
      StyledDocument document = (StyledDocument)getDocument();
      int x = e.getX();
      int y = e.getY();
      if (!hasTextAt(document, x, y) &&
          !hasTextAt(document, x + 3, y) &&
          !hasTextAt(document, x - 3, y) &&
          !hasTextAt(document, x, y + 3) &&
          !hasTextAt(document, x, y - 3)) {
        initialClick = e.getPoint();
      }
    }
    super.processMouseEvent(e);
  }

  private boolean hasTextAt(StyledDocument document, int x, int y) {
    Element element = document.getCharacterElement(viewToModel(new Point(x, y)));
    try {
      String text = document.getText(element.getStartOffset(), element.getEndOffset() - element.getStartOffset());
      if (StringUtil.isEmpty(text.trim())) {
        return false;
      }
    }
    catch (BadLocationException ignored) {
      return false;
    }
    return true;
  }

  @Override
  protected void processMouseMotionEvent(MouseEvent e) {
    if (e.getID() == MouseEvent.MOUSE_DRAGGED && myHint != null && initialClick != null) {
      Point location = myHint.getLocationOnScreen();
      myHint.setLocation(new Point(location.x + e.getX() - initialClick.x, location.y + e.getY() - initialClick.y));
      e.consume();
      return;
    }
    super.processMouseMotionEvent(e);
  }
}
