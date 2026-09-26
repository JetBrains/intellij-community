// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation;

import com.intellij.lang.documentation.DocumentationImageResolver;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.WindowMoveListener;
import com.intellij.ui.scale.JBUIScale;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.KeyStroke;
import java.awt.Component;
import java.awt.FontMetrics;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseEvent;
import java.util.Map;

import static com.intellij.codeInsight.documentation.DocumentationHtmlUtil.getDocPopupPreferredMinWidth;

@Internal
public final class DocumentationHintEditorPane extends DocumentationEditorPane {

  private final Project myProject;
  private final WindowMoveListener moveListener = new DocumentationWindowMoveListener(this);

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

  private boolean customSettingsEnabled = false;

  @Internal
  public boolean isCustomSettingsEnabled() {
    return customSettingsEnabled;
  }

  @Internal
  public void setCustomSettingsEnabled(boolean customSettingsEnabled) {
    this.customSettingsEnabled = customSettingsEnabled;
  }

  public void setHint(@NotNull JBPopup hint) {
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
      removeFocusListener(focusAdapter);
    });
  }

  @Override
  protected void processMouseEvent(MouseEvent e) {
    processWindowMoveListenerMouseEvent(e);
    if (!e.isConsumed()) {
      super.processMouseEvent(e);
    }
  }

  @Override
  protected void processMouseMotionEvent(MouseEvent e) {
    processWindowMoveListenerMouseEvent(e);
    if (!e.isConsumed()) {
      super.processMouseMotionEvent(e);
    }
  }

  private void processWindowMoveListenerMouseEvent(MouseEvent e) {
    if (PopupUtil.getPopupContainerFor(this) == null) return; // disable the move listener when this thing is in a tool window
    // We can't use moveListener.installTo() because there are other listeners, and we can't depend on their order.
    // The move listener must be invoked first, and if it consumes the event, then we must stop.
    // Otherwise, it'll lead to weird effects like the text selection changing while the popup is being moved.
    switch (e.getID()) {
      case MouseEvent.MOUSE_PRESSED -> moveListener.mousePressed(e);
      case MouseEvent.MOUSE_RELEASED -> moveListener.mouseReleased(e);
      case MouseEvent.MOUSE_CLICKED -> moveListener.mouseClicked(e);
      case MouseEvent.MOUSE_EXITED -> moveListener.mouseExited(e);
      case MouseEvent.MOUSE_ENTERED -> moveListener.mouseEntered(e);
      case MouseEvent.MOUSE_MOVED -> moveListener.mouseMoved(e);
      case MouseEvent.MOUSE_DRAGGED -> moveListener.mouseDragged(e);
    }
  }

  @Override
  protected int getExtraHeight(int height, int contentPreferredWidth, int expectedWidth) {
    if (!customSettingsEnabled) return 0;
    if (contentPreferredWidth <= getDocPopupPreferredMinWidth()) return 0;
    int lines = (int)Math.ceil(contentPreferredWidth * 1.0 / expectedWidth);
    if (lines <= 1) return 0;
    FontMetrics fontMetrics = this.getFontMetrics(getFont());
    int lineHeight = fontMetrics.getHeight();
    return JBUIScale.scale((lines - 1) * lineHeight);
  }

  @Nullable
  private Integer forcedMinWidth;

  @Internal
  @Override
  protected int getForcedMinWidth() {
    return forcedMinWidth == null ? 0 : forcedMinWidth;
  }

  @Internal
  public void setForcedMinWidth(int width) {
    forcedMinWidth = width;
  }
}
