/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.ActiveComponent;
import com.intellij.ui.ListUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.speedSearch.ListWithFilter;
import com.intellij.ui.treeStructure.treetable.TreeTable;
import com.intellij.util.BooleanFunction;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
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
  private final ArrayList<KeyStroke> myAdditionalKeystrokes = new ArrayList<>();
  private Runnable myItemChosenRunnable;
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

  private final List<JBPopupListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private String myAd;
  private Dimension myMinSize;
  private ActiveComponent myCommandButton;
  private final List<Pair<ActionListener,KeyStroke>> myKeyboardActions = new ArrayList<>();
  private Component mySettingsButtons;
  private boolean myAutoselectOnMouseMove = true;

  private Function<Object,String> myItemsNamer = null;
  private boolean myMayBeParent;
  private int myAdAlignment = SwingUtilities.LEFT;
  private boolean myModalContext;
  private boolean myCloseOnEnter = true;
  private boolean myCancelOnWindowDeactivation = true;
  private boolean myUseForXYLocation;
  @Nullable private Processor<JBPopup> myCouldPin;

  public PopupChooserBuilder setCancelOnClickOutside(boolean cancelOnClickOutside) {
    myCancelOnClickOutside = cancelOnClickOutside;
    return this;
  }

  private boolean myCancelOnClickOutside = true;



  public JScrollPane getScrollPane() {
    return myScrollPane;
  }

  private JScrollPane myScrollPane;

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
    myItemChosenRunnable = runnable;
    return this;
  }

  @NotNull
  public PopupChooserBuilder setSouthComponent(@NotNull JComponent cmp) {
    mySouthComponent = cmp;
    return this;
  }

  @NotNull
  public PopupChooserBuilder setCouldPin(@Nullable Processor<JBPopup> callback){
    myCouldPin = callback;
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

  public PopupChooserBuilder setUseDimensionServiceForXYLocation(boolean use) {
    myUseForXYLocation = use;
    return this;
  }

  public PopupChooserBuilder setCancelCallback(Computable<Boolean> callback) {
    myCancelCallback = callback;
    return this;
  }

  public PopupChooserBuilder setCommandButton(@NotNull ActiveComponent commandButton) {
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

  public PopupChooserBuilder setModalContext(boolean modalContext) {
    myModalContext = modalContext;
    return this;
  }

  @NotNull
  public JBPopup createPopup() {
    final JList list;
    BooleanFunction<KeyEvent> keyEventHandler = null;
    if (myChooserComponent instanceof JList) {
      list = (JList)myChooserComponent;
      myChooserComponent = ListWithFilter.wrap(list, new MyListWrapper(list), myItemsNamer);
      keyEventHandler = keyEvent -> keyEvent.isConsumed();
    }
    else {
      list = null;
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
      public void mouseReleased(MouseEvent e) {
        if (UIUtil.isActionClick(e, MouseEvent.MOUSE_RELEASED) && !UIUtil.isSelectionButtonDown(e) && !e.isConsumed()) {
          if (myCloseOnEnter) {
            closePopup(true, e, true);
          }
          else {
            myItemChosenRunnable.run();
          }
        }
      }
    });

    registerClosePopupKeyboardAction(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, true), false);
    if (myCloseOnEnter) {
      registerClosePopupKeyboardAction(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, true), true);
    }
    else {
      registerKeyboardAction(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent event) {
          myItemChosenRunnable.run();
        }
      });
    }
    for (KeyStroke keystroke : myAdditionalKeystrokes) {
      registerClosePopupKeyboardAction(keystroke, true);
    }

    if (myChooserComponent instanceof ListWithFilter) {
      myScrollPane = ((ListWithFilter)myChooserComponent).getScrollPane();
    }
    else if (myChooserComponent instanceof JTable) {
      myScrollPane = createScrollPane((JTable)myChooserComponent);
    }
    else if (myChooserComponent instanceof JTree) {
      myScrollPane = createScrollPane((JTree)myChooserComponent);
    }
    else {
      throw new IllegalStateException("PopupChooserBuilder is intended to be constructed with one of JTable, JTree, JList components");
    }

    myScrollPane.getViewport().setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    Insets viewportPadding = UIUtil.getListViewportPadding();
    ((JComponent)myScrollPane.getViewport().getView()).setBorder(BorderFactory.createEmptyBorder(viewportPadding.top, viewportPadding.left, viewportPadding.bottom, viewportPadding.right));

    if (myChooserComponent instanceof ListWithFilter) {
      addCenterComponentToContentPane(contentPane, myChooserComponent);
    }
    else {
      addCenterComponentToContentPane(contentPane, myScrollPane);
    }

    if (mySouthComponent != null) {
      addSouthComponentToContentPane(contentPane, mySouthComponent);
    }

    if (myEastComponent != null) {
      addEastComponentToContentPane(contentPane, myEastComponent);
    }

    ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(contentPane, myChooserComponent);
    for (JBPopupListener each : myListeners) {
      builder.addListener(each);
    }

    builder.setDimensionServiceKey(null, myDimensionServiceKey, myUseForXYLocation)
      .setRequestFocus(myRequestFocus)
      .setResizable(myForceResizable)
      .setMovable(myForceMovable)
      .setTitle(myForceMovable ? myTitle : null)
      .setCancelCallback(myCancelCallback)
      .setAlpha(myAlpha)
      .setFocusOwners(myFocusOwners)
      .setCancelKeyEnabled(myCancelKeyEnabled)
      .setAdText(myAd, myAdAlignment)
      .setKeyboardActions(myKeyboardActions)
      .setMayBeParent(myMayBeParent)
      .setLocateWithinScreenBounds(true)
      .setCancelOnOtherWindowOpen(true)
      .setModalContext(myModalContext)
      .setCancelOnWindowDeactivation(myCancelOnWindowDeactivation)
      .setCancelOnClickOutside(myCancelOnClickOutside)
      .setCouldPin(myCouldPin);

    if (keyEventHandler != null) {
      builder.setKeyEventHandler(keyEventHandler);
    }

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

  protected void addEastComponentToContentPane(JPanel contentPane, JComponent component) {
    contentPane.add(component, BorderLayout.EAST);
  }

  protected void addSouthComponentToContentPane(JPanel contentPane, JComponent component) {
    contentPane.add(component, BorderLayout.SOUTH);
  }

  protected void addCenterComponentToContentPane(JPanel contentPane, JComponent component) {
    contentPane.add(component, BorderLayout.CENTER);
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
    registerPopupKeyboardAction(keyStroke, new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        if (!shouldPerformAction && myChooserComponent instanceof ListWithFilter) {
          if (((ListWithFilter)myChooserComponent).resetFilter()) return;
        }
        closePopup(shouldPerformAction, null, shouldPerformAction);
      }
    });
  }

  private void registerPopupKeyboardAction(final KeyStroke keyStroke, AbstractAction action) {
    myChooserComponent.registerKeyboardAction(action, keyStroke, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
  }

  private void closePopup(boolean shouldPerformAction, MouseEvent e, boolean isOk) {
    if (shouldPerformAction) {
      myPopup.setFinalRunnable(myItemChosenRunnable);
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

  public PopupChooserBuilder setSettingButton(Component abutton) {
    mySettingsButtons = abutton;
    return this;
  }

  public PopupChooserBuilder setMayBeParent(boolean mayBeParent) {
    myMayBeParent = mayBeParent;
    return this;
  }

  public PopupChooserBuilder setCloseOnEnter(boolean closeOnEnter) {
    myCloseOnEnter = closeOnEnter;
    return this;
  }

  private class MyListWrapper extends JBScrollPane implements DataProvider {
    @SuppressWarnings({"FieldAccessedSynchronizedAndUnsynchronized"})
    private final JList myList;

    private MyListWrapper(final JList list) {
      super(UIUtil.isUnderAquaLookAndFeel() ? 0 : -1);
      list.setVisibleRowCount(15);
      setViewportView(list);


      if (myAutoselectOnMouseMove) {
        ListUtil.installAutoSelectOnMouseMove(list);
      }

      ScrollingUtil.installActions(list);

      setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
      myList = list;
    }

    @Nullable
    public Object getData(@NonNls String dataId) {
      if (PlatformDataKeys.SELECTED_ITEM.is(dataId)){
        return myList.getSelectedValue();
      }
      if (PlatformDataKeys.SELECTED_ITEMS.is(dataId)){
        return myList.getSelectedValues();
      }
      return null;
    }

    @Override
    public Dimension getPreferredSize() {
      if (isPreferredSizeSet()) {
        return super.getPreferredSize();
      }
      Dimension size = myList.getPreferredSize();
      size.height = Math.min(size.height, myList.getPreferredScrollableViewportSize().height);
      JScrollBar bar = getVerticalScrollBar();
      if (bar != null) size.width += bar.getPreferredSize().width;
      return size;
    }

    public void setBorder(Border border) {
      if (myList != null){
        myList.setBorder(border);
      }
    }

    public void requestFocus() {
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
        IdeFocusManager.getGlobalInstance().requestFocus(myList, true);
      });
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
    setAdText(ad, SwingUtilities.LEFT);
    return this;
  }

  public PopupChooserBuilder setAdText(String ad, int alignment) {
    myAd = ad;
    myAdAlignment = alignment;
    return this;
  }

  public PopupChooserBuilder setCancelOnWindowDeactivation(boolean cancelOnWindowDeactivation) {
    myCancelOnWindowDeactivation = cancelOnWindowDeactivation;
    return this;
  }
}
