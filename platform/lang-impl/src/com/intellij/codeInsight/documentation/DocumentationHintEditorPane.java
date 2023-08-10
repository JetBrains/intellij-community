// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation;

import com.intellij.lang.documentation.DocumentationImageResolver;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.*;
import java.util.Map;
import java.util.function.Function;

@Internal
public final class DocumentationHintEditorPane extends DocumentationEditorPane {

  private final Project myProject;

  public DocumentationHintEditorPane(
    @NotNull Project project,
    @NotNull Map<KeyStroke, ActionListener> keyboardActions,
    @NotNull DocumentationImageResolver imageResolver,
    @NotNull Function<? super @NotNull String, ? extends @Nullable Icon> iconResolver
  ) {
    super(keyboardActions, imageResolver, iconResolver);
    myProject = project;
  }

  public DocumentationHintEditorPane(
    @NotNull Project project,
    @NotNull Map<KeyStroke, ActionListener> keyboardActions,
    @NotNull DocumentationImageResolver imageResolver
  ) {
    super(keyboardActions, imageResolver, (key) -> {
      return null;
    });
    myProject = project;
  }

  public void setHint(@NotNull JBPopup hint) {
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
    Disposer.register(hint, () -> {
      myHint = null;
      removeFocusListener(focusAdapter);
    });
  }

  private JBPopup myHint; // lateinit
  private Point myInitialPress;

  @Override
  protected void processMouseEvent(MouseEvent e) {
    if (e.getID() == MouseEvent.MOUSE_PRESSED && myHint != null) {
      myInitialPress = null;
      StyledDocument document = (StyledDocument)getDocument();
      int x = e.getX();
      int y = e.getY();
      if (!hasTextAt(document, x, y) &&
          !hasTextAt(document, x + 3, y) &&
          !hasTextAt(document, x - 3, y) &&
          !hasTextAt(document, x, y + 3) &&
          !hasTextAt(document, x, y - 3)) {
        myInitialPress = e.getPoint();
      }
    }
    super.processMouseEvent(e);
  }

  private boolean hasTextAt(StyledDocument document, int x, int y) {
    Element element = document.getCharacterElement(viewToModel(new Point(x, y)));
    try {
      String text = document.getText(element.getStartOffset(), element.getEndOffset() - element.getStartOffset());
      return !text.trim().isEmpty();
    }
    catch (BadLocationException ignored) {
      return false;
    }
  }

  @Override
  protected void processMouseMotionEvent(MouseEvent e) {
    if (e.getID() == MouseEvent.MOUSE_DRAGGED && myHint != null && myInitialPress != null) {
      Point location = myHint.getLocationOnScreen();
      myHint.setLocation(new Point(location.x + e.getX() - myInitialPress.x, location.y + e.getY() - myInitialPress.y));
      e.consume();
      return;
    }
    super.processMouseMotionEvent(e);
  }
}
