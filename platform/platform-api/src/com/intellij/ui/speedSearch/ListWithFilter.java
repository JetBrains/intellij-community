/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.LightColors;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.UIBundle;
import com.intellij.util.Function;
import com.intellij.util.ui.ComponentWithEmptyText;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class ListWithFilter<T> extends JPanel {
  private final JList myList;
  private final JTextField mySpeedSearchPatternField;
  private final NameFilteringListModel<T> myModel;
  private final JScrollPane myScroller;
  private final MySpeedSearch mySpeedSearch;

  public static JComponent wrap(JList list) {
    return wrap(list, ScrollPaneFactory.createScrollPane(list), StringUtil.createToStringFunction(Object.class));
  }

  public static <T> JComponent wrap(JList list, JScrollPane scroller, Function<T, String> namer) {
    return new ListWithFilter<T>(list, scroller, namer);
  }

  private ListWithFilter(JList list, JScrollPane scroller, Function<T, String> namer) {
    super(new BorderLayout());

    if (list instanceof ComponentWithEmptyText) {
      ((ComponentWithEmptyText)list).getEmptyText().setText(UIBundle.message("message.noMatchesFound"));
    }

    myList = list;
    myScroller = scroller;

    mySpeedSearchPatternField = new JTextField();
    mySpeedSearchPatternField.setFocusable(false);
    mySpeedSearchPatternField.setVisible(false);

    add(mySpeedSearchPatternField, BorderLayout.NORTH);
    add(myScroller, BorderLayout.CENTER);

    mySpeedSearch = new MySpeedSearch();
    mySpeedSearch.setEnabled(namer != null);

    myList.addKeyListener(new KeyAdapter() {
      public void keyPressed(final KeyEvent e) {
        mySpeedSearch.process(e);
      }
    });

    myModel = new NameFilteringListModel<T>(myList, namer, new Condition<String>() {
      public boolean value(String s) {
        return mySpeedSearch.shouldBeShowing(s);
      }
    }, mySpeedSearch);

    setBackground(list.getBackground());
    //setFocusable(true);
  }

  public boolean resetFilter() {
    boolean hadPattern = mySpeedSearch.isHoldingFilter();
    if (mySpeedSearchPatternField.isVisible()) {
      mySpeedSearch.reset();
    }
    return hadPattern;
  }

  private class MySpeedSearch extends SpeedSearch {
    boolean searchFieldShown = false;

    protected void update() {
      mySpeedSearchPatternField.setBackground(new JTextField().getBackground());
      onSpeedSearchPatternChanged();
      mySpeedSearchPatternField.setText(getFilter());
      if (isHoldingFilter() && !searchFieldShown) {
        mySpeedSearchPatternField.setVisible(true);
        searchFieldShown = true;
      }
      else if (!isHoldingFilter() && searchFieldShown) {
        mySpeedSearchPatternField.setVisible(false);
        searchFieldShown = false;
      }

      revalidate();
    }

    private void revalidate() {
      JBPopup popup = PopupUtil.getPopupContainerFor(mySpeedSearchPatternField);
      if (popup != null) {
        popup.pack(false, true);
      }
      ListWithFilter.this.revalidate();
    }
  }

  protected void onSpeedSearchPatternChanged() {
    myModel.refilter();
    if (myModel.getSize() > 0) {
      int fullMatchIndex = myModel.getClosestMatchIndex();
      if (fullMatchIndex != -1) {
        myList.setSelectedIndex(fullMatchIndex);
      }

      if (myModel.getSize() <= myList.getSelectedIndex() || !myModel.contains((T)myList.getSelectedValue())) {
        myList.setSelectedIndex(0);
      }
    }
    else {
      mySpeedSearchPatternField.setBackground(LightColors.RED);
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
