// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.openapi.ui.popup.JBPopupAdapter;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public abstract class SearchPopupController {
  private final PluginSearchTextField myTextField;
  protected SearchPopup myPopup;
  private final JBPopupListener mySearchPopupListener = new JBPopupAdapter() {
    @Override
    public void onClosed(LightweightWindowEvent event) {
      myPopup = null;
    }
  };

  public SearchPopupController(@NotNull PluginSearchTextField searchTextField) {
    myTextField = searchTextField;
  }

  public void handleShowPopup() {
    String query = myTextField.getText();
    int length = query.length();
    int position = myTextField.getTextEditor().getCaretPosition();

    if (position < length) {
      if (query.charAt(position) == ' ') {
        if (position == 0 || query.charAt(position - 1) == ' ') {
          showAttributesPopup(null);
          return;
        }
      }
      else {
        hidePopup();
        handleAppendToQuery();
        return;
      }
    }
    else if (query.charAt(position - 1) == ' ') {
      showAttributesPopup(null);
      return;
    }

    Pair<String, String> attribute = parseAttributeInQuery(query, position);
    if (attribute.second == null) {
      showAttributesPopup(attribute.first);
    }
    else {
      handleShowAttributeValuesPopup(attribute.first, attribute.second);
    }
  }

  @NotNull
  private static Pair<String, String> parseAttributeInQuery(@NotNull String query, int end) {
    int index = end - 1;
    String value = null;

    while (index >= 0) {
      char ch = query.charAt(index);
      if (ch == ':') {
        value = query.substring(index + 1, end);
        end = index + 1;
        index--;
        while (index >= 0) {
          if (query.charAt(index) == ' ') {
            break;
          }
          index--;
        }
        break;
      }
      if (ch == ' ') {
        break;
      }
      index--;
    }

    return Pair.create(StringUtil.trimStart(query.substring(index + 1, end), "-"), value);
  }

  public void showAttributesPopup(@Nullable String namePrefix) {
    CollectionListModel<Object> model = new CollectionListModel<>(getAttributes());

    if (noPrefixSearchValues(model, namePrefix)) {
      return;
    }

    boolean async = myPopup != null;

    if (myPopup == null || myPopup.type != SearchPopup.Type.AttributeName || !myPopup.isValid()) {
      hidePopup();
      createPopup(model, SearchPopup.Type.AttributeName);
    }
    else {
      myPopup.model.replaceAll(model.getItems());
      myPopup.callback.prefix = namePrefix;
      myPopup.update();
      return;
    }

    SearchPopupCallback callback = new SearchPopupCallback() {
      @Override
      public void consume(String value) {
        appendSearchText(value, prefix);
        handleShowAttributeValuesPopup(value, null);
      }
    };
    callback.prefix = namePrefix;

    ColoredListCellRenderer renderer = new ColoredListCellRenderer() {
      @Override
      protected void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean hasFocus) {
        append((String)value, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      }
    };

    myPopup.createAndShow(callback, renderer, async);
  }

  protected void createPopup(@NotNull CollectionListModel<Object> model, @NotNull SearchPopup.Type type) {
    myPopup = new SearchPopup(myTextField, mySearchPopupListener, type);
    myPopup.model = model;
  }

  private boolean noPrefixSearchValues(@NotNull CollectionListModel<Object> model, @Nullable String prefix) {
    if (StringUtil.isEmptyOrSpaces(prefix)) {
      return false;
    }

    int index = 0;
    while (index < model.getSize()) {
      String attribute = (String)model.getElementAt(index);
      if (attribute.equals(prefix)) {
        hidePopup();
        return true;
      }
      if (StringUtil.startsWithIgnoreCase(attribute, prefix)) {
        index++;
      }
      else {
        model.remove(index);
      }
    }

    if (model.isEmpty()) {
      showPopupForQuery();
      return true;
    }

    return false;
  }

  private void handleShowAttributeValuesPopup(@NotNull String name, @Nullable String valuePrefix) {
    List<String> values = getValues(name);
    if (ContainerUtil.isEmpty(values)) {
      showPopupForQuery();
      return;
    }

    CollectionListModel<Object> model = new CollectionListModel<>(values);

    if (noPrefixSearchValues(model, valuePrefix)) {
      return;
    }

    if (myPopup == null || myPopup.type != SearchPopup.Type.AttributeValue || !myPopup.isValid()) {
      hidePopup();
      createPopup(model, SearchPopup.Type.AttributeValue);
    }
    else {
      myPopup.model.replaceAll(model.getItems());
      myPopup.callback.prefix = valuePrefix;
      myPopup.update();
      return;
    }

    SearchPopupCallback callback = new SearchPopupCallback() {
      @Override
      public void consume(String value) {
        if (StringUtil.containsAnyChar(value, " ,:")) {
          value = "\"" + value + "\"";
        }
        appendSearchText(value, prefix);
        handleAppendAttributeValue();
      }
    };
    callback.prefix = valuePrefix;

    ColoredListCellRenderer renderer = new ColoredListCellRenderer() {
      @Override
      protected void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean hasFocus) {
        append((String)value);
      }
    };

    myPopup.createAndShow(callback, renderer, true);
  }

  @NotNull
  protected abstract List<String> getAttributes();

  @Nullable
  protected abstract List<String> getValues(@NotNull String attribute);

  protected abstract void showPopupForQuery();

  protected void handleAppendToQuery() {
  }

  protected void handleAppendAttributeValue() {
  }

  public void hidePopup() {
    if (myPopup != null) {
      myPopup.hide();
      myPopup = null;
    }
  }

  private void appendSearchText(@NotNull String value, @Nullable String prefix) {
    String text = myTextField.getText();
    String suffix = "";
    JBTextField editor = myTextField.getTextEditor();
    int position = editor.getCaretPosition();

    if (myPopup != null) {
      myPopup.skipCaretEvent = true;
    }

    if (position < text.length()) {
      suffix = text.substring(position);
      text = text.substring(0, position);
    }

    if (prefix == null) {
      myTextField.setTextIgnoreEvents(text + value + suffix);
    }
    else if (value.startsWith(prefix)) {
      myTextField.setTextIgnoreEvents(text + value.substring(prefix.length()) + suffix);
    }
    else if (StringUtil.startsWithIgnoreCase(value, prefix)) {
      myTextField.setTextIgnoreEvents(text.substring(0, text.length() - prefix.length()) + value + suffix);
    }
    else {
      myTextField.setTextIgnoreEvents(text + value + suffix);
    }

    editor.setCaretPosition(myTextField.getText().length() - suffix.length());
  }

  public boolean handleEnter(@NotNull KeyEvent event) {
    if (myPopup != null && myPopup.list != null && myPopup.list.getSelectedIndex() != -1) {
      myPopup.list.dispatchEvent(event);
      return true;
    }
    return false;
  }

  public boolean handleUpDown(@NotNull KeyEvent event) {
    if (myPopup != null && myPopup.list != null) {
      if (event.getKeyCode() == KeyEvent.VK_DOWN && myPopup.list.getSelectedIndex() == -1) {
        myPopup.list.setSelectedIndex(0);
      }
      else {
        myPopup.list.dispatchEvent(event);
      }
    }
    return false;
  }
}