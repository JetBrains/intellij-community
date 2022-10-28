// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation.render;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.MouseShortcut;
import com.intellij.openapi.editor.CustomFoldRegion;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.util.Objects;

public interface DocRenderLinkActivationHandler {
  void activateLink(HyperlinkEvent event, @NotNull DocRenderer renderer);

  static Rectangle2D getLocation(HyperlinkEvent event) {
    Element element = event.getSourceElement();
    if (element == null) return null;

    Rectangle2D location = null;
    try {
      location = ((JEditorPane)event.getSource()).modelToView2D(element.getStartOffset());
    }
    catch (BadLocationException ignored) {
    }
    return location;
  }

  static boolean isGotoDeclarationEvent() {
    KeymapManager keymapManager = KeymapManager.getInstance();
    if (keymapManager == null) return false;
    AWTEvent event = IdeEventQueue.getInstance().getTrueCurrentEvent();
    if (!(event instanceof MouseEvent)) return false;
    MouseShortcut mouseShortcut = KeymapUtil.createMouseShortcut((MouseEvent)event);
    return keymapManager.getActiveKeymap().getActionIds(mouseShortcut).contains(IdeActions.ACTION_GOTO_DECLARATION);
  }

  @NotNull
  static Point popupPosition(@NotNull Rectangle2D linkLocationWithinInlay, DocRenderer renderer) {
    CustomFoldRegion foldRegion = renderer.getItem().getFoldRegion();
    if (foldRegion == null) return new Point(0, 0);
    Point rendererPosition = Objects.requireNonNull(foldRegion.getLocation());
    Rectangle relativeBounds = renderer.getEditorPaneBoundsWithinRenderer(foldRegion.getWidthInPixels(), foldRegion.getHeightInPixels());
    return new Point(
      rendererPosition.x + relativeBounds.x + (int)linkLocationWithinInlay.getX(),
      rendererPosition.y + relativeBounds.y + (int)Math.ceil(linkLocationWithinInlay.getMaxY())
    );
  }
}
