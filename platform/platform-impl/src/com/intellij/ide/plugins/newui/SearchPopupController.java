// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui;

import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.util.Collection;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public abstract class SearchPopupController {
  protected final PluginSearchTextField myTextField;
  @ApiStatus.Internal
  protected SearchPopup myPopup;
  private final JBPopupListener mySearchPopupListener = new JBPopupListener() {
    @Override
    public void onClosed(@NotNull LightweightWindowEvent event) {
      myPopup = null;
    }
  };

  public SearchPopupController(@NotNull PluginSearchTextField searchTextField) {
    myTextField = searchTextField;
  }

  public void handleShowPopup() {
    String query = myTextField.getText();
    int length = query.length();
    int position = getCaretPosition();

    if (position < length) {
      if (query.charAt(position) != ' ') {
        handleShowPopupForQuery();
        return;
      }
    }
    else if (query.charAt(position - 1) == ' ') {
      handleShowPopupForQuery();
      return;
    }

    Ref<Integer> startPosition = new Ref<>();
    Pair<String, String> attribute = parseAttributeInQuery(query, position, startPosition);
    if (attribute.second == null) {
      showAttributesPopup(attribute.first, startPosition.get());
    }
    else {
      handleShowAttributeValuesPopup(attribute.first, attribute.second, startPosition.get());
    }
  }

  private int getCaretPosition() {
    return myTextField.getTextEditor().getCaretPosition();
  }

  private static @NotNull Pair<String, String> parseAttributeInQuery(@NotNull String query, int end, @NotNull Ref<? super Integer> startPosition) {
    int index = end - 1;
    String value = null;

    while (index >= 0) {
      char ch = query.charAt(index);
      if (ch == ':') {
        value = query.substring(index + 1, end);
        startPosition.set(index + 1);
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

    if (startPosition.isNull()) {
      startPosition.set(index + 1);
    }

    return Pair.create(query.substring(index + 1, end), value);
  }

  public void showAttributesPopup(@Nullable String namePrefix, int caretPosition) {
    CollectionListModel<Object> model = new CollectionListModel<>(getAttributes());

    if (noPrefixSearchValues(model, namePrefix)) {
      return;
    }

    boolean async = myPopup != null;

    if (updatePopupOrCreate(SearchPopup.Type.AttributeName, model, namePrefix, caretPosition)) {
      return;
    }

    createAndShow(async, new SearchPopupCallback(namePrefix) {
      @Override
      public void consume(String value) {
        appendSearchText(value, prefix);
        handleShowAttributeValuesPopup(value, null, getCaretPosition());
      }
    });
  }

  private void handleShowAttributeValuesPopup(@NotNull String name, @Nullable String valuePrefix, int caretPosition) {
    Collection<String> values = getValues(name);
    if (ContainerUtil.isEmpty(values)) {
      handleShowPopupForQuery();
      return;
    }

    CollectionListModel<Object> model = new CollectionListModel<>(values);

    if (noPrefixSearchValues(model, valuePrefix)) {
      return;
    }

    if (updatePopupOrCreate(SearchPopup.Type.AttributeValue, model, valuePrefix, caretPosition)) {
      return;
    }

    createAndShow(true, new SearchPopupCallback(valuePrefix) {
      @Override
      public void consume(String value) {
        appendSearchText(SearchQueryParser.wrapAttribute(value), prefix);
        handleShowPopupForQuery();
      }
    });
  }

  private boolean updatePopupOrCreate(@NotNull SearchPopup.Type type,
                                      @NotNull CollectionListModel<Object> model,
                                      @Nullable String prefix,
                                      int caretPosition) {
    if (myPopup == null || myPopup.type != type || !myPopup.isValid()) {
      createPopup(type, model, caretPosition);
    }
    else {
      myPopup.model.replaceAll(model.getItems());
      myPopup.callback.prefix = prefix;
      myPopup.caretPosition = caretPosition;
      myPopup.update();
      return true;
    }
    return false;
  }

  @ApiStatus.Internal
  protected void createPopup(@NotNull SearchPopup.Type type, @NotNull CollectionListModel<Object> model, int caretPosition) {
    hidePopup();
    myPopup = new SearchPopup(myTextField, mySearchPopupListener, type, model, caretPosition);
  }

  private void createAndShow(boolean async, @NotNull SearchPopupCallback callback) {
    ColoredListCellRenderer renderer = new ColoredListCellRenderer() {
      @Override
      protected void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean hasFocus) {
        append((String)value);
      }
    };

    myPopup.createAndShow(callback, renderer, async);
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
      handleShowPopupForQuery();
      return true;
    }

    return false;
  }

  protected abstract @NotNull List<String> getAttributes();

  protected abstract @Nullable Collection<String> getValues(@NotNull String attribute); // TODO to be replaced with SortedSet

  private void handleShowPopupForQuery() {
    hidePopup();
    showPopupForQuery();
  }

  protected abstract void showPopupForQuery();

  public boolean isPopupShow() {
    return myPopup != null && myPopup.isValid();
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
    int position = getCaretPosition();

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
    else if (StringUtil.startsWithIgnoreCase(value, prefix) || StringUtil.startsWithIgnoreCase(value, "\"" + prefix)) {
      myTextField.setTextIgnoreEvents(text.substring(0, text.length() - prefix.length()) + value + suffix);
    }
    else {
      myTextField.setTextIgnoreEvents(text + value + suffix);
    }

    myTextField.getTextEditor().setCaretPosition(myTextField.getText().length() - suffix.length());
  }

  public boolean handleEnter(@NotNull KeyEvent event) {
    if (myPopup != null && myPopup.list != null && myPopup.list.getSelectedIndex() != -1) {
      myPopup.list.dispatchEvent(event);
      return true;
    }
    handleEnter();
    return false;
  }

  protected void handleEnter() {
  }

  public boolean handleUpDown(@NotNull KeyEvent event) {
    if (myPopup != null && myPopup.list != null) {
      if (event.getKeyCode() == KeyEvent.VK_DOWN && myPopup.list.getSelectedIndex() == -1) {
        myPopup.list.setSelectedIndex(0);
        handlePopupListFirstSelection();
      }
      else {
        myPopup.list.dispatchEvent(event);
      }
    }
    return false;
  }

  protected void handlePopupListFirstSelection() {
  }
}