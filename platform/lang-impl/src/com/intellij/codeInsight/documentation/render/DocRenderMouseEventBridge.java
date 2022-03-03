// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.documentation.render;

import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.editor.ex.EditorEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.awt.AWTAccessor;

import java.awt.*;
import java.awt.event.MouseEvent;

class DocRenderMouseEventBridge implements EditorMouseListener, EditorMouseMotionListener {
  private final DocRenderSelectionManager mySelectionManager;
  private DocRenderer.EditorPane myMouseOverPane;
  private DocRenderer.EditorPane myDragPane;

  DocRenderMouseEventBridge(DocRenderSelectionManager selectionManager) {
    mySelectionManager = selectionManager;
  }

  @Override
  public void mouseMoved(@NotNull EditorMouseEvent event) {
    if (event.getArea() != EditorMouseEventArea.EDITING_AREA) return;

    DocRenderer.EditorPane currentPane = redispatchEvent(event, MouseEvent.MOUSE_MOVED, null);
    if (currentPane == null) {
      restoreCursor();
    }
    else {
      ((EditorEx)event.getEditor()).setCustomCursor(DocRenderMouseEventBridge.class, currentPane.getCursor());
      if (currentPane != myMouseOverPane) {
        if (myMouseOverPane != null) {
          dispatchMouseExitEvent(myMouseOverPane);
        }
        myMouseOverPane = currentPane;
      }
    }
  }

  @Override
  public void mouseDragged(@NotNull EditorMouseEvent e) {
    if (e.getArea() != EditorMouseEventArea.EDITING_AREA) return;

    checkPaneSelection(redispatchEvent(e, MouseEvent.MOUSE_DRAGGED, myDragPane));
    if (myDragPane != null) e.consume();
  }

  @Override
  public void mouseExited(@NotNull EditorMouseEvent event) {
    if (event.getArea() != EditorMouseEventArea.EDITING_AREA) return;

    restoreCursor();
  }

  @Override
  public void mousePressed(@NotNull EditorMouseEvent event) {
    if (event.getArea() != EditorMouseEventArea.EDITING_AREA) return;

    myDragPane = redispatchEvent(event, MouseEvent.MOUSE_PRESSED, null);
    checkPaneSelection(myDragPane);
  }

  @Override
  public void mouseReleased(@NotNull EditorMouseEvent event) {
    if (event.getArea() != EditorMouseEventArea.EDITING_AREA) return;

    checkPaneSelection(redispatchEvent(event, MouseEvent.MOUSE_RELEASED, null));
    myDragPane = null;
  }

  @Override
  public void mouseClicked(@NotNull EditorMouseEvent event) {
    if (event.getArea() != EditorMouseEventArea.EDITING_AREA) return;

    checkPaneSelection(redispatchEvent(event, MouseEvent.MOUSE_CLICKED, null));
  }

  private void restoreCursor() {
    if (myMouseOverPane != null) {
      dispatchMouseExitEvent(myMouseOverPane);
      ((EditorEx)myMouseOverPane.getEditor()).setCustomCursor(DocRenderMouseEventBridge.class, null);
      myMouseOverPane = null;
    }
  }

  private void checkPaneSelection(@Nullable DocRenderer.EditorPane pane) {
    if (pane != null && pane.hasSelection()) {
      mySelectionManager.setPaneWithSelection(pane);
    }
  }

  @Nullable
  private static DocRenderer.EditorPane redispatchEvent(@NotNull EditorMouseEvent event,
                                                        // we need a separately passed eventId because EditorImpl dispatches MOUSE_RELEASED
                                                        // events to both 'mouseReleased' and 'mouseClicked' methods in listeners
                                                        int eventId,
                                                        @Nullable DocRenderer.EditorPane targetPane) {
    MouseEvent mouseEvent = event.getMouseEvent();
    Point mousePoint = mouseEvent.getPoint();
    Editor editor = event.getEditor();
    FoldRegion foldRegion = event.getCollapsedFoldRegion();
    if (foldRegion instanceof CustomFoldRegion) {
      CustomFoldRegion cfr = (CustomFoldRegion)foldRegion;
      CustomFoldRegionRenderer r = cfr.getRenderer();
      if (r instanceof DocRenderer) {
        DocRenderer renderer = (DocRenderer)r;
        Rectangle relativeBounds = renderer.getEditorPaneBoundsWithinRenderer(cfr.getWidthInPixels(), cfr.getHeightInPixels());
        Point location = cfr.getLocation();
        assert location != null;
        int x = mousePoint.x - location.x - relativeBounds.x;
        int y = mousePoint.y - location.y - relativeBounds.y;
        if (x >= 0 && x < relativeBounds.width && y >= 0 && y < relativeBounds.height) {
          DocRenderer.EditorPane editorPane = renderer.getRendererComponent(editor, relativeBounds.width);
          if (eventId != MouseEvent.MOUSE_DRAGGED || targetPane == editorPane) {
            int button = mouseEvent.getButton();
            dispatchEvent(editorPane, new MouseEvent(editorPane, eventId, 0, mouseEvent.getModifiersEx(), x, y,
                                                     mouseEvent.getClickCount(), false,
                                                     // hack to process middle-button clicks (JEditorPane ignores them)
                                                     button == MouseEvent.BUTTON2 ? MouseEvent.BUTTON1 : button));
            return editorPane;
          }
        }
      }
    }
    return null;
  }

  private static void dispatchEvent(@NotNull DocRenderer.EditorPane editorPane, @NotNull MouseEvent event) {
    editorPane.doWithRepaintTracking(() -> AWTAccessor.getComponentAccessor().processEvent(editorPane, event));
  }

  private static void dispatchMouseExitEvent(@NotNull DocRenderer.EditorPane editorPane) {
    dispatchEvent(editorPane, new MouseEvent(editorPane, MouseEvent.MOUSE_EXITED, 0, 0, 0, 0, 0, false));
  }
}
