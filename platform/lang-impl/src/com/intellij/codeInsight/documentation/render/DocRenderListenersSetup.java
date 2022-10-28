// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation.render;

import com.intellij.ide.ui.LafManagerListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FoldingListener;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.FontInfo;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Consumer;

public class DocRenderListenersSetup {
  private static final Key<Disposable> LISTENERS_DISPOSABLE = Key.create("doc.render.listeners.disposable");

  public static void setupListeners(@NotNull Editor editor, boolean disable, Consumer<MessageBusConnection> installAdditionalListeners) {
    if (disable) {
      Disposable listenersDisposable = editor.getUserData(LISTENERS_DISPOSABLE);
      if (listenersDisposable != null) {
        Disposer.dispose(listenersDisposable);
        editor.putUserData(LISTENERS_DISPOSABLE, null);
      }
    }
    else {
      if (editor.getUserData(LISTENERS_DISPOSABLE) == null) {
        MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
        connection.setDefaultHandler(() -> DocRenderUpdater.updateRenderers(editor, true));
        connection.subscribe(EditorColorsManager.TOPIC);
        connection.subscribe(LafManagerListener.TOPIC);

        DocRenderSelectionManager selectionManager = new DocRenderSelectionManager(editor);
        Disposer.register(connection, selectionManager);

        DocRenderMouseEventBridge mouseEventBridge = new DocRenderMouseEventBridge(selectionManager);
        editor.addEditorMouseListener(mouseEventBridge, connection);
        editor.addEditorMouseMotionListener(mouseEventBridge, connection);

        IconVisibilityController iconVisibilityController = new IconVisibilityController();
        editor.addEditorMouseListener(iconVisibilityController, connection);
        editor.addEditorMouseMotionListener(iconVisibilityController, connection);
        editor.getScrollingModel().addVisibleAreaListener(iconVisibilityController, connection);
        Disposer.register(connection, iconVisibilityController);

        editor.getScrollingModel().addVisibleAreaListener(new MyVisibleAreaListener(editor), connection);
        ((EditorEx)editor).getFoldingModel().addListener(new MyFoldingListener(), connection);

        installAdditionalListeners.accept(connection);

        Disposer.register(connection, () -> DocRenderer.clearCachedLoadingPane(editor));

        editor.putUserData(LISTENERS_DISPOSABLE, connection);
      }
    }
  }

  private static final class MyVisibleAreaListener implements VisibleAreaListener {
    private int lastWidth;
    private AffineTransform lastFrcTransform;

    private MyVisibleAreaListener(@NotNull Editor editor) {
      lastWidth = DocRenderer.calcWidth(editor);
      lastFrcTransform = getTransform(editor);
    }

    private static AffineTransform getTransform(Editor editor) {
      return FontInfo.getFontRenderContext(editor.getContentComponent()).getTransform();
    }

    @Override
    public void visibleAreaChanged(@NotNull VisibleAreaEvent e) {
      if (e.getNewRectangle().isEmpty()) return; // ignore switching between tabs
      Editor editor = e.getEditor();
      int newWidth = DocRenderer.calcWidth(editor);
      AffineTransform transform = getTransform(editor);
      if (newWidth != lastWidth || !Objects.equals(transform, lastFrcTransform)) {
        lastWidth = newWidth;
        lastFrcTransform = transform;
        DocRenderUpdater.updateRenderers(editor, false);
      }
    }
  }

  private static class MyFoldingListener implements FoldingListener {
    @Override
    public void beforeFoldRegionDisposed(@NotNull FoldRegion region) {
      if (region instanceof CustomFoldRegion) {
        CustomFoldRegionRenderer renderer = ((CustomFoldRegion)region).getRenderer();
        if (renderer instanceof DocRenderer) {
          ((DocRenderer)renderer).dispose();
        }
      }
    }
  }

  private static class IconVisibilityController implements EditorMouseListener, EditorMouseMotionListener, VisibleAreaListener, Disposable {
    private DocRenderItem myCurrentItem;
    private Editor myQueuedEditor;

    @Override
    public void mouseMoved(@NotNull EditorMouseEvent e) {
      doUpdate(e.getEditor(), e);
    }

    @Override
    public void mouseExited(@NotNull EditorMouseEvent e) {
      doUpdate(e.getEditor(), e);
    }

    @Override
    public void visibleAreaChanged(@NotNull VisibleAreaEvent e) {
      Editor editor = e.getEditor();
      if (((EditorImpl)editor).isCursorHidden()) return;
      if (myQueuedEditor == null) {
        myQueuedEditor = editor;
        // delay update: multiple visible area updates within same EDT event will cause only one icon update,
        // and we'll not observe the item in inconsistent state during toggling
        SwingUtilities.invokeLater(() -> {
          if (myQueuedEditor != null && !myQueuedEditor.isDisposed()) {
            doUpdate(myQueuedEditor, null);
          }
          myQueuedEditor = null;
        });
      }
    }

    private void doUpdate(@NotNull Editor editor, @Nullable EditorMouseEvent event) {
      int y = 0;
      int offset = -1;
      if (event == null) {
        PointerInfo info = MouseInfo.getPointerInfo();
        if (info != null) {
          Point screenPoint = info.getLocation();
          JComponent component = editor.getComponent();

          Point componentPoint = new Point(screenPoint);
          SwingUtilities.convertPointFromScreen(componentPoint, component);

          if (new Rectangle(component.getSize()).contains(componentPoint)) {
            Point editorPoint = new Point(screenPoint);
            SwingUtilities.convertPointFromScreen(editorPoint, editor.getContentComponent());
            y = editorPoint.y;
            offset = editor.visualPositionToOffset(new VisualPosition(editor.yToVisualLine(y), 0));
          }
        }
      }
      else {
        y = event.getMouseEvent().getY();
        offset = event.getOffset();
      }
      DocRenderItem item = offset < 0 ? null : findItem(editor, y, offset);
      if (item != myCurrentItem) {
        if (myCurrentItem != null) myCurrentItem.setIconVisible(false);
        myCurrentItem = item;
        if (myCurrentItem != null) myCurrentItem.setIconVisible(true);
      }
    }

    private static DocRenderItem findItem(Editor editor, int y, int neighborOffset) {
      Document document = editor.getDocument();
      int lineNumber = document.getLineNumber(neighborOffset);
      int searchStartOffset = document.getLineStartOffset(Math.max(0, lineNumber - 1));
      int searchEndOffset = document.getLineEndOffset(lineNumber);
      Collection<? extends DocRenderItem> items = DocRenderItemManager.getInstance().getItems(editor);
      assert items != null;
      for (DocRenderItem item : items) {
        RangeHighlighter highlighter = item.getHighlighter();
        if (highlighter.isValid() && highlighter.getStartOffset() <= searchEndOffset && highlighter.getEndOffset() >= searchStartOffset) {
          int itemStartY = 0;
          int itemEndY = 0;
          if (item.getFoldRegion() == null) {
            itemStartY = editor.visualLineToY(editor.offsetToVisualLine(highlighter.getStartOffset(), false));
            itemEndY = editor.visualLineToY(editor.offsetToVisualLine(highlighter.getEndOffset(), true)) + editor.getLineHeight();
          }
          else {
            CustomFoldRegion cfr = item.getFoldRegion();
            Point location = cfr.getLocation();
            if (location != null) {
              itemStartY = location.y;
              itemEndY = itemStartY + cfr.getHeightInPixels();
            }
          }
          if (y >= itemStartY && y < itemEndY) return item;
          break;
        }
      }
      return null;
    }

    @Override
    public void dispose() {
      myCurrentItem = null;
      myQueuedEditor = null;
    }
  }
}
