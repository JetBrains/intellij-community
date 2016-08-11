/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * @author max
 */
package com.intellij.ui.speedSearch;

import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.util.Function;
import com.intellij.util.ui.ComponentWithEmptyText;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class ListWithFilter<T> extends JPanel implements DataProvider {
  private final JList myList;
  private final SearchTextField mySearchField = new SearchTextField(false);
  private final NameFilteringListModel<T> myModel;
  private final JScrollPane myScroller;
  private final MySpeedSearch mySpeedSearch;

  @Override
  public Object getData(@NonNls String dataId) {
    if (SpeedSearchSupply.SPEED_SEARCH_CURRENT_QUERY.is(dataId)) {
      return mySearchField.getText();
    }
    return null;
  }

  public static boolean isSearchActive(JList list) {
    final ListWithFilter listWithFilter = UIUtil.getParentOfType(ListWithFilter.class, list);
    return listWithFilter != null && listWithFilter.mySpeedSearch.searchFieldShown;
  }

  public static JComponent wrap(JList list) {
    return wrap(list, ScrollPaneFactory.createScrollPane(list), StringUtil.createToStringFunction(Object.class));
  }

  public static <T> JComponent wrap(JList list, JScrollPane scroller, Function<T, String> namer) {
    return new ListWithFilter<>(list, scroller, namer);
  }

  private ListWithFilter(JList list, JScrollPane scroller, Function<T, String> namer) {
    super(new BorderLayout());

    if (list instanceof ComponentWithEmptyText) {
      ((ComponentWithEmptyText)list).getEmptyText().setText(UIBundle.message("message.noMatchesFound"));
    }

    myList = list;
    myScroller = scroller;

    mySearchField.getTextEditor().setFocusable(false);
    mySearchField.setVisible(false);

    add(mySearchField, BorderLayout.NORTH);
    add(myScroller, BorderLayout.CENTER);

    mySpeedSearch = new MySpeedSearch();
    mySpeedSearch.setEnabled(namer != null);

    myList.addKeyListener(new KeyAdapter() {
      public void keyPressed(final KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_A && (e.isControlDown() || e.isMetaDown())) {
          return;
        }
        mySpeedSearch.process(e);
      }
    });
    //new AnAction(){
    //  @Override
    //  public void actionPerformed(AnActionEvent e) {
    //    final InputEvent event = e.getInputEvent();
    //    if (event instanceof KeyEvent) {
    //      mySpeedSearch.process((KeyEvent)event);
    //    }
    //  }
    //
    //  @Override
    //  public void update(AnActionEvent e) {
    //    e.getPresentation().setEnabled(mySpeedSearch.searchFieldShown);
    //  }
    //}.registerCustomShortcutSet(CustomShortcutSet.fromString("BACK_SPACE", "DELETE"), list);
    final int selectedIndex = myList.getSelectedIndex();
    final int modelSize = myList.getModel().getSize();
    myModel = new NameFilteringListModel<>(myList, namer, s -> mySpeedSearch.shouldBeShowing(s), mySpeedSearch);
    if (myModel.getSize() == modelSize) {
      myList.setSelectedIndex(selectedIndex);
    }

    setBackground(list.getBackground());
    //setFocusable(true);
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

  private class MySpeedSearch extends SpeedSearch {
    boolean searchFieldShown;
    boolean myInUpdate;

    private MySpeedSearch() {
      // native mac "clear button" is not captured by SearchTextField.onFieldCleared
      mySearchField.addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(DocumentEvent e) {
          if (myInUpdate) return;
          if (mySearchField.getText().isEmpty()) {
            mySpeedSearch.reset();
          }
        }
      });
    }

    public void update() {
      myInUpdate = true;
      mySearchField.getTextEditor().setBackground(UIUtil.getTextFieldBackground());
      onSpeedSearchPatternChanged();
      mySearchField.setText(getFilter());
      if (isHoldingFilter() && !searchFieldShown) {
        mySearchField.setVisible(true);
        searchFieldShown = true;
      }
      else if (!isHoldingFilter() && searchFieldShown) {
        mySearchField.setVisible(false);
        searchFieldShown = false;
      }

      myInUpdate = false;
      revalidate();
    }

    private void revalidate() {
      JBPopup popup = PopupUtil.getPopupContainerFor(mySearchField);
      if (popup != null) {
        popup.pack(false, true);
      }
      ListWithFilter.this.revalidate();
    }
  }

  protected void onSpeedSearchPatternChanged() {
    T prevSelection = (T)myList.getSelectedValue(); // save to restore the selection on filter drop
    myModel.refilter();
    if (myModel.getSize() > 0) {
      int fullMatchIndex = mySpeedSearch.isHoldingFilter() ? myModel.getClosestMatchIndex() : myModel.getElementIndex(prevSelection);
      if (fullMatchIndex != -1) {
        myList.setSelectedIndex(fullMatchIndex);
      }

      if (myModel.getSize() <= myList.getSelectedIndex() || !myModel.contains((T)myList.getSelectedValue())) {
        myList.setSelectedIndex(0);
      }
    }
    else {
      mySearchField.getTextEditor().setBackground(LightColors.RED);
      revalidate();
    }
  }

  public JList getList() {
    return myList;
  }

  public JScrollPane getScrollPane() {
    return myScroller;
  }

  @Override
  public void requestFocus() {
    myList.requestFocus();
  }
}
