// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.speedSearch;

import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.*;
import com.intellij.util.Function;
import com.intellij.util.ui.ComponentWithEmptyText;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;

public final class ListWithFilter<T> extends JPanel implements DataProvider {
  private final JList<T> myList;
  private final SearchTextField mySearchField = new SearchTextField(false);
  private final NameFilteringListModel<T> myModel;
  private final JScrollPane myScrollPane;
  private final MySpeedSearch mySpeedSearch;
  private final boolean mySearchFieldWithoutBorder;
  private boolean myAutoPackHeight = true;
  private final boolean mySearchAlwaysVisible;

  @Override
  public Object getData(@NotNull @NonNls String dataId) {
    if (PlatformDataKeys.SPEED_SEARCH_TEXT.is(dataId)) {
      return mySearchField.getText();
    }
    return null;
  }

  public static @NotNull <T> JComponent wrap(@NotNull JList<? extends T> list,
                                             @NotNull JScrollPane scrollPane,
                                             @Nullable Function<? super T, String> namer) {
    return wrap(list, scrollPane, namer, false);
  }

  public static @NotNull <T> JComponent wrap(@NotNull JList<? extends T> list,
                                             @NotNull JScrollPane scrollPane,
                                             @Nullable Function<? super T, String> namer,
                                             boolean highlightAllOccurrences) {
    return new ListWithFilter<>(list, scrollPane, namer, highlightAllOccurrences, false, false);
  }

  public static @NotNull <T> JComponent wrap(@NotNull JList<? extends T> list,
                                             @NotNull JScrollPane scrollPane,
                                             @Nullable Function<? super T, String> namer,
                                             boolean highlightAllOccurrences,
                                             boolean searchFieldAlwaysVisible,
                                             boolean searchFieldWithoutBorder) {
    return new ListWithFilter<>(list, scrollPane, namer, highlightAllOccurrences, searchFieldAlwaysVisible, searchFieldWithoutBorder);
  }

  private ListWithFilter(@NotNull JList<T> list,
                         @NotNull JScrollPane scrollPane,
                         @Nullable Function<? super T, String> namer,
                         boolean highlightAllOccurrences,
                         boolean searchAlwaysVisible,
                         boolean searchFieldWithoutBorder) {
    super(new BorderLayout());

    if (list instanceof ComponentWithEmptyText) {
      ((ComponentWithEmptyText)list).getEmptyText().setText(UIBundle.message("message.noMatchesFound"));
    }

    myList = list;
    myScrollPane = scrollPane;

    mySearchAlwaysVisible = searchAlwaysVisible;
    mySearchFieldWithoutBorder = searchFieldWithoutBorder;

    mySearchField.getTextEditor().setFocusable(false);
    mySearchField.setVisible(mySearchAlwaysVisible);

    Color background = list.getBackground();
    if (mySearchFieldWithoutBorder) {
      mySearchField.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
      mySearchField.getTextEditor().setBorder(JBUI.Borders.empty());
      if (background != null) {
        UIUtil.setBackgroundRecursively(mySearchField, background);
      }
    }


    add(mySearchField, BorderLayout.NORTH);
    add(myScrollPane, BorderLayout.CENTER);

    mySpeedSearch = new MySpeedSearch(highlightAllOccurrences);
    mySpeedSearch.setEnabled(namer != null);

    myList.addKeyListener(mySpeedSearch);
    int selectedIndex = myList.getSelectedIndex();
    int modelSize = myList.getModel().getSize();
    myModel = new NameFilteringListModel<>(
      myList.getModel(), namer, mySpeedSearch::shouldBeShowing,
      () -> StringUtil.notNullize(mySpeedSearch.getFilter()));
    myList.setModel(myModel);
    if (myModel.getSize() == modelSize) {
      myList.setSelectedIndex(selectedIndex);
    }
    myList.getActionMap().put(TransferHandler.getPasteAction().getValue(Action.NAME), new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        mySpeedSearch.type(CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor));
        mySpeedSearch.update();
      }
    });

    setBackground(background);
    //setFocusable(true);
  }

  @Override
  protected void processFocusEvent(FocusEvent e) {
    super.processFocusEvent(e);
    if (e.getID() == FocusEvent.FOCUS_GAINED) {
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myList, true));
    }
  }

  public boolean resetFilter() {
    boolean hadPattern = mySpeedSearch.isHoldingFilter();
    if (mySearchField.isVisible()) {
      mySpeedSearch.reset();
    }
    return hadPattern;
  }

  public SpeedSearch getSpeedSearch() {
    return mySpeedSearch;
  }

  private final class MySpeedSearch extends SpeedSearch {
    boolean searchFieldShown = mySearchAlwaysVisible;
    boolean myInUpdate;

    private MySpeedSearch(boolean highlightAllOccurrences) {
      super(highlightAllOccurrences);
      // native mac "clear button" is not captured by SearchTextField.onFieldCleared
      mySearchField.addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(@NotNull DocumentEvent e) {
          if (myInUpdate) return;
          if (mySearchField.getText().isEmpty()) {
            mySpeedSearch.reset();
          }
        }
      });
      installSupplyTo(myList);
    }

    @Override
    public void update() {
      myInUpdate = true;

      Color searchBg = mySearchFieldWithoutBorder ? myList.getBackground() : UIUtil.getTextFieldBackground();
      mySearchField.getTextEditor().setBackground(searchBg);
      onSpeedSearchPatternChanged();
      mySearchField.setText(getFilter());
      if (!mySearchAlwaysVisible) {
        if (isHoldingFilter() && !searchFieldShown) {
          mySearchField.setVisible(true);
          searchFieldShown = true;
        }
        else if (!isHoldingFilter() && searchFieldShown) {
          mySearchField.setVisible(false);
          searchFieldShown = false;
        }
      }

      myInUpdate = false;
      revalidate();
    }

    @Override
    public void noHits() {
      mySearchField.getTextEditor().setBackground(LightColors.RED);
    }

    private void revalidate() {
      JBPopup popup = PopupUtil.getPopupContainerFor(mySearchField);
      if (popup != null) {
        popup.pack(false, myAutoPackHeight);
      }
      ListWithFilter.this.revalidate();
    }
  }

  private void onSpeedSearchPatternChanged() {
    T prevSelection = myList.getSelectedValue(); // save to restore the selection on filter drop
    myModel.refilter();
    if (myModel.getSize() > 0) {
      int fullMatchIndex = mySpeedSearch.isHoldingFilter() ? myModel.getClosestMatchIndex() : myModel.getElementIndex(prevSelection);
      if (fullMatchIndex != -1) {
        myList.setSelectedIndex(fullMatchIndex);
      }

      if (myModel.getSize() <= myList.getSelectedIndex() || !myModel.contains(myList.getSelectedValue())) {
        myList.setSelectedIndex(0);
      }
    }
    else {
      mySpeedSearch.noHits();
      revalidate();
    }
  }

  public @NotNull JList<T> getList() {
    return myList;
  }

  public @NotNull JScrollPane getScrollPane() {
    return myScrollPane;
  }

  public void setAutoPackHeight(boolean autoPackHeight) {
    myAutoPackHeight = autoPackHeight;
  }

  @Override
  public void requestFocus() {
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myList, true));
  }
}
