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

package com.intellij.openapi.ui.popup;

import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.ListScrollingUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.speedSearch.ListWithFilter;
import com.intellij.ui.treeStructure.treetable.TreeTable;
import com.intellij.util.Function;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 */
public class PopupChooserBuilder {
  private JComponent myChooserComponent;
  private String myTitle;
  private final ArrayList<KeyStroke> myAdditionalKeystrokes = new ArrayList<KeyStroke>();
  private Runnable myItemChoosenRunnable;
  private JComponent mySouthComponent;
  private JComponent myEastComponent;

  private JBPopup myPopup;

  private boolean myRequestFocus = true;
  private boolean myForceResizable = false;
  private boolean myForceMovable = false;
  private String myDimensionServiceKey = null;
  private Computable<Boolean> myCancelCallback;
  private boolean myAutoselect = true;
  private float myAlpha;
  private Component[] myFocusOwners = new Component[0];
  private boolean myCancelKeyEnabled = true;

  ArrayList<JBPopupListener> myListeners = new ArrayList<JBPopupListener>();
  private String myAd;
  private Dimension myMinSize;
  private InplaceButton myCommandButton;
  private final List<Pair<ActionListener,KeyStroke>> myKeyboardActions = new ArrayList<Pair<ActionListener, KeyStroke>>();
  private Component mySettingsButtons;
  private boolean myAutoselectOnMouseMove = true;

  private Function<Object,String> myItemsNamer = null;

  public PopupChooserBuilder(@NotNull JList list) {
    myChooserComponent = list;
  }

  public PopupChooserBuilder(@NotNull JTable table) {
    myChooserComponent = table;
  }

  public PopupChooserBuilder(@NotNull JTree tree) {
    myChooserComponent = tree;
  }

  @NotNull
  public PopupChooserBuilder setTitle(@NotNull @Nls String title) {
    myTitle = title;
    return this;
  }

  @NotNull
  public PopupChooserBuilder addAdditionalChooseKeystroke(@Nullable KeyStroke keyStroke) {
    if (keyStroke != null) {
      myAdditionalKeystrokes.add(keyStroke);
    }
    return this;
  }

  @NotNull
  public PopupChooserBuilder setItemChoosenCallback(@NotNull Runnable runnable) {
    myItemChoosenRunnable = runnable;
    return this;
  }

  @NotNull
  public PopupChooserBuilder setSouthComponent(@NotNull JComponent cmp) {
    mySouthComponent = cmp;
    return this;
  }

  @NotNull
  public PopupChooserBuilder setEastComponent(@NotNull JComponent cmp) {
    myEastComponent = cmp;
    return this;
  }


  public PopupChooserBuilder setRequestFocus(final boolean requestFocus) {
    myRequestFocus = requestFocus;
    return this;
  }

  public PopupChooserBuilder setResizable(final boolean forceResizable) {
    myForceResizable = forceResizable;
    return this;
  }


  public PopupChooserBuilder setMovable(final boolean forceMovable) {
    myForceMovable = forceMovable;
    return this;
  }

  public PopupChooserBuilder setDimensionServiceKey(@NonNls String key){
    myDimensionServiceKey = key;
    return this;
  }

  public PopupChooserBuilder setCancelCallback(Computable<Boolean> callback) {
    myCancelCallback = callback;
    return this;
  }

  public PopupChooserBuilder setCommandButton(@NotNull InplaceButton commandButton) {
    myCommandButton = commandButton;
    return this;
  }

  public PopupChooserBuilder setAlpha(final float alpha) {
    myAlpha = alpha;
    return this;
  }

  public PopupChooserBuilder setAutoselectOnMouseMove(final boolean doAutoSelect) {
    myAutoselectOnMouseMove = doAutoSelect;
    return this;
  }
  
  public PopupChooserBuilder setFilteringEnabled(Function<Object, String> namer) {
    myItemsNamer = namer;
    return this;
  }

  @NotNull
  public JBPopup createPopup() {
    JList list = null;
    if (myChooserComponent instanceof JList) {
      list = (JList)myChooserComponent;
      myChooserComponent = ListWithFilter.wrap(list, new MyListWrapper(list), myItemsNamer);
    }

    JPanel contentPane = new JPanel(new BorderLayout());
    if (!myForceMovable && myTitle != null) {
      JLabel label = new JLabel(myTitle);
      label.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
      label.setHorizontalAlignment(SwingConstants.CENTER);
      contentPane.add(label, BorderLayout.NORTH);
    }

    if (list != null) {
      if (list.getSelectedIndex() == -1 && myAutoselect) {
        list.setSelectedIndex(0);
      }
    }


    (list != null ? list : myChooserComponent).addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        if (UIUtil.isActionClick(e) && !isSelectionButtonDown(e) && !e.isConsumed()) {
          closePopup(true, e, true);
        }
      }
    });

    registerClosePopupKeyboardAction(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), false);
    registerClosePopupKeyboardAction(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), true);
    for (KeyStroke keystroke : myAdditionalKeystrokes) {
      registerClosePopupKeyboardAction(keystroke, true);
    }

    final JScrollPane scrollPane;
    if (myChooserComponent instanceof ListWithFilter) {
      scrollPane = ((ListWithFilter)myChooserComponent).getScrollPane();
    }
    else if (myChooserComponent instanceof JTable) {
      scrollPane = createScrollPane((JTable)myChooserComponent);
    }
    else if (myChooserComponent instanceof JTree) {
      scrollPane = createScrollPane((JTree)myChooserComponent);
    }
    else {
      throw new IllegalStateException("PopupChooserBuilder is intended to be constructed with one of JTable, JTree, JList components");
    }

    scrollPane.getViewport().setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    ((JComponent)scrollPane.getViewport().getView()).setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

    if (myChooserComponent instanceof ListWithFilter) {
      contentPane.add(myChooserComponent, BorderLayout.CENTER);
    }
    else {
      contentPane.add(scrollPane, BorderLayout.CENTER);
    }

    if (mySouthComponent != null) {
      contentPane.add(mySouthComponent, BorderLayout.SOUTH);
    }

    if (myEastComponent != null) {
      contentPane.add(myEastComponent, BorderLayout.EAST);
    }

    ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(contentPane, myChooserComponent);
    for (JBPopupListener each : myListeners) {
      builder.addListener(each);
    }

    builder.setDimensionServiceKey(null, myDimensionServiceKey, false).setRequestFocus(myRequestFocus).setResizable(myForceResizable)
      .setMovable(myForceMovable).setTitle(myForceMovable ? myTitle : null).setCancelCallback(myCancelCallback).setAlpha(myAlpha)
      .setFocusOwners(myFocusOwners).setCancelKeyEnabled(myCancelKeyEnabled && !(myChooserComponent instanceof ListWithFilter)).
      setAdText(myAd).setKeyboardActions(myKeyboardActions);

    if (myCommandButton != null) {
      builder.setCommandButton(myCommandButton);
    }

    if (myMinSize != null) {
      builder.setMinSize(myMinSize);
    }
    if (mySettingsButtons != null) {
      builder.setSettingButtons(mySettingsButtons);
    }
    myPopup = builder.createPopup();
    return myPopup;
  }

  public PopupChooserBuilder setMinSize(final Dimension dimension) {
    myMinSize = dimension;
    return this;
  }

  public PopupChooserBuilder registerKeyboardAction(KeyStroke keyStroke, ActionListener actionListener) {
    myKeyboardActions.add(Pair.create(actionListener, keyStroke));
    return this;
  }

  private void registerClosePopupKeyboardAction(final KeyStroke keyStroke, final boolean shouldPerformAction) {
    myChooserComponent.registerKeyboardAction(new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        if (!shouldPerformAction && myChooserComponent instanceof ListWithFilter) {
          if (((ListWithFilter)myChooserComponent).resetFilter()) return;
        }
        closePopup(shouldPerformAction, null, shouldPerformAction);
      }
    }, keyStroke, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
  }

  private void closePopup(boolean shouldPerformAction, MouseEvent e, boolean isOk) {
    if (shouldPerformAction) {
      myPopup.setFinalRunnable(myItemChoosenRunnable);
    }

    if (isOk) {
      myPopup.closeOk(e);
    } else {
      myPopup.cancel(e);
    }
  }

  @NotNull
  private JScrollPane createScrollPane(final JTable table) {
    if (table instanceof TreeTable) {
      TreeUtil.expandAll(((TreeTable)table).getTree());
    }

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(table);

    scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

    if (table.getSelectedRow() == -1) {
      table.getSelectionModel().setSelectionInterval(0, 0);
    }

    if (table.getRowCount() >= 20) {
      scrollPane.getViewport().setPreferredSize(new Dimension(table.getPreferredScrollableViewportSize().width, 300));
    }
    else {
      scrollPane.getViewport().setPreferredSize(table.getPreferredSize());
    }

    if (myAutoselectOnMouseMove) {
      table.addMouseMotionListener(new MouseMotionAdapter() {
        boolean myIsEngaged = false;
        public void mouseMoved(MouseEvent e) {
          if (myIsEngaged) {
            int index = table.rowAtPoint(e.getPoint());
            table.getSelectionModel().setSelectionInterval(index, index);
          }
          else {
            myIsEngaged = true;
          }
        }
      });
    }

    return scrollPane;
  }

  @NotNull
  private JScrollPane createScrollPane(final JTree tree) {
    TreeUtil.expandAll(tree);

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(tree);

    scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

    if (tree.getSelectionCount() == 0) {
      tree.setSelectionRow(0);
    }

    if (tree.getRowCount() >= 20) {
      scrollPane.getViewport().setPreferredSize(new Dimension(tree.getPreferredScrollableViewportSize().width, 300));
    }
    else {
      scrollPane.getViewport().setPreferredSize(tree.getPreferredSize());
    }

    if (myAutoselectOnMouseMove) {
      tree.addMouseMotionListener(new MouseMotionAdapter() {
        boolean myIsEngaged = false;
        public void mouseMoved(MouseEvent e) {
          if (myIsEngaged) {
            final Point p = e.getPoint();
            int index = tree.getRowForLocation(p.x, p.y);
            tree.setSelectionRow(index);
          }
          else {
            myIsEngaged = true;
          }
        }
      });
    }

    return scrollPane;
  }

  public PopupChooserBuilder setAutoSelectIfEmpty(final boolean autoselect) {
    myAutoselect = autoselect;
    return this;
  }

  public PopupChooserBuilder setCancelKeyEnabled(final boolean enabled) {
    myCancelKeyEnabled = enabled;
    return this;
  }

  public PopupChooserBuilder addListener(final JBPopupListener listener) {
    myListeners.add(listener);
    return this;
  }

  private static boolean isSelectionButtonDown(MouseEvent e) {
    return e.isShiftDown() || e.isControlDown() || e.isMetaDown();
  }

  public PopupChooserBuilder setSettingButton(Component abutton) {
    mySettingsButtons = abutton;
    return this;
  }

  private class MyListWrapper extends JBScrollPane implements DataProvider {
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    private final JList myList;

    private MyListWrapper(final JList list) {
      super(list);
      if (myAutoselectOnMouseMove) {
        list.addMouseMotionListener(new MouseMotionAdapter() {
          boolean myIsEngaged = false;
          public void mouseMoved(MouseEvent e) {
            if (myIsEngaged && !isSelectionButtonDown(e)) {
              Point point = e.getPoint();
              int index = list.locationToIndex(point);
              list.setSelectedIndex(index);
            }
            else {
              myIsEngaged = true;
            }
          }
        });
      }

      ListScrollingUtil.installActions(list);

      int modelSize = list.getModel().getSize();
      setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
      if (modelSize > 0 && modelSize <= 20) {
        list.setVisibleRowCount(0);
        getViewport().setPreferredSize(list.getPreferredSize());
      }
      else {
        list.setVisibleRowCount(20);
      }
      myList = list;
    }


    @Nullable
    public Object getData(@NonNls String dataId) {
      if (PlatformDataKeys.SELECTED_ITEM.is(dataId)){
        return myList.getSelectedValue();
      }
      return null;
    }

    public void setBorder(Border border) {
      if (myList != null){
        myList.setBorder(border);
      }
    }

    public void requestFocus() {
      myList.requestFocus();
    }

    public synchronized void addMouseListener(MouseListener l) {
      myList.addMouseListener(l);
    }
  }

  @NotNull
  public PopupChooserBuilder setFocusOwners(@NotNull Component[] focusOwners) {
    myFocusOwners = focusOwners;
    return this;
  }

  @NotNull
  public PopupChooserBuilder setAdText(String ad) {
    myAd = ad;
    return this;
  }

}
