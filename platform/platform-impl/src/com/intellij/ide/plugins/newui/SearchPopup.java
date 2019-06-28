// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Consumer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/**
 * @author Alexander Lobas
 */
public class SearchPopup extends ComponentAdapter implements CaretListener {
  public final Type type;

  private final JBPopupListener myListener;
  private final JBTextField myEditor;
  private JBPopup myPopup;
  private LightweightWindowEvent myEvent;
  private Component myDialogComponent;

  public final CollectionListModel<Object> model;
  public JList<Object> list;

  public int caretPosition;

  public SearchPopupCallback callback;

  public boolean skipCaretEvent;

  public Object data;

  public enum Type {
    AttributeName, AttributeValue, SearchQuery
  }

  public SearchPopup(@NotNull SearchTextField searchTextField,
                     @NotNull JBPopupListener listener,
                     @NotNull Type type,
                     @NotNull CollectionListModel<Object> model,
                     int caretPosition) {
    myEditor = searchTextField.getTextEditor();
    myListener = listener;
    this.type = type;
    this.model = model;
    this.caretPosition = caretPosition;
  }

  public void createAndShow(@NotNull Consumer callback, @NotNull ColoredListCellRenderer renderer, boolean async) {
    if (callback instanceof SearchPopupCallback) {
      this.callback = (SearchPopupCallback)callback;
    }

    Insets ipad = renderer.getIpad();
    ipad.left = ipad.right = getXOffset();
    renderer.setFont(myEditor.getFont());

    //noinspection unchecked,deprecation
    myPopup = JBPopupFactory.getInstance().createListPopupBuilder(list = new JBList<>(model))
      .setMovable(false).setResizable(false).setRequestFocus(false)
      .setItemChosenCallback(callback).setFont(myEditor.getFont())
      .setRenderer(renderer).createPopup();
    myEvent = new LightweightWindowEvent(myPopup);

    skipCaretEvent = true;
    myPopup.addListener(myListener);
    myEditor.addCaretListener(this);

    myDialogComponent = myEditor.getRootPane().getParent();
    if (myDialogComponent != null) {
      myDialogComponent.addComponentListener(this);
    }

    if (async) {
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(this::show);
    }
    else {
      show();
    }
  }

  private static int getXOffset() {
    int i = UIUtil.isUnderWin10LookAndFeel() ? 5 : UIUtil.getListCellHPadding();
    return JBUIScale.scale(i);
  }

  @NotNull
  private Point getPopupLocation() {
    Point location;
    try {
      Rectangle view = myEditor.modelToView(caretPosition);
      location = new Point((int)view.getMaxX(), (int)view.getMaxY());
    }
    catch (BadLocationException ignore) {
      location = myEditor.getCaret().getMagicCaretPosition();
    }

    SwingUtilities.convertPointToScreen(location, myEditor);
    location.x -= getXOffset() + JBUIScale.scale(2);
    location.y += 2;

    return location;
  }

  public boolean isValid() {
    return myPopup.isVisible() && myPopup.getContent().getParent() != null;
  }

  public void update() {
    skipCaretEvent = true;
    myPopup.setLocation(getPopupLocation());
    myPopup.pack(true, true);
  }

  private void show() {
    if (myPopup != null) {
      list.clearSelection();
      myPopup.showInScreenCoordinates(myEditor, getPopupLocation());
    }
  }

  public void hide() {
    myEditor.removeCaretListener(this);
    if (myDialogComponent != null) {
      myDialogComponent.removeComponentListener(this);
      myDialogComponent = null;
    }
    if (myPopup != null) {
      myPopup.cancel();
      myPopup = null;
    }
  }

  @Override
  public void caretUpdate(CaretEvent e) {
    if (skipCaretEvent) {
      skipCaretEvent = false;
    }
    else {
      hide();
      myListener.onClosed(myEvent);
    }
  }

  @Override
  public void componentMoved(ComponentEvent e) {
    if (myPopup != null && isValid()) {
      update();
    }
  }

  @Override
  public void componentResized(ComponentEvent e) {
    componentMoved(e);
  }
}