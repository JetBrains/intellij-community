// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.openapi.fileChooser.ex.FileTextFieldImpl;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;

/**
 * @author Alexander Lobas
 */
public class SearchPopup implements CaretListener {
  public final Type type;

  private final JBPopupListener myListener;
  private final JBTextField myEditor;
  private JBPopup myPopup;
  private LightweightWindowEvent myEvent;

  public CollectionListModel<Object> model;
  public JList<Object> list;

  public SearchPopupCallback callback;

  public boolean skipCaretEvent;

  public Object data;

  public enum Type {
    AttributeName, AttributeValue, SearchQuery
  }

  public SearchPopup(@NotNull SearchTextField searchTextField, @NotNull JBPopupListener listener, @NotNull Type type) {
    myEditor = searchTextField.getTextEditor();
    myListener = listener;
    this.type = type;
  }

  public boolean isValid() {
    return myPopup.isVisible() && myPopup.getContent().getParent() != null;
  }

  public void update() {
    skipCaretEvent = true;
    myPopup.setLocation(FileTextFieldImpl.getLocationForCaret(myEditor));
    myPopup.pack(true, true);
  }

  public void createAndShow(@NotNull Consumer callback, @NotNull ColoredListCellRenderer renderer, boolean async) {
    if (callback instanceof SearchPopupCallback) {
      this.callback = (SearchPopupCallback)callback;
    }

    myPopup = JBPopupFactory.getInstance().createListPopupBuilder(list = new JBList<>(model))
      .setMovable(false).setResizable(false).setRequestFocus(false)
      .setItemChosenCallback(callback)
      .setRenderer(renderer).createPopup();
    myEvent = new LightweightWindowEvent(myPopup);

    skipCaretEvent = true;
    myPopup.addListener(myListener);
    myEditor.addCaretListener(this);

    if (async) {
      SwingUtilities.invokeLater(this::show);
    }
    else {
      show();
    }
  }

  private void show() {
    list.clearSelection();
    myPopup.showInScreenCoordinates(myEditor, FileTextFieldImpl.getLocationForCaret(myEditor));
  }

  public void hide() {
    myEditor.removeCaretListener(this);
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
}