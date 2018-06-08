// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.ui.popup;

import com.intellij.openapi.ui.ListComponentUpdater;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.ActiveComponent;
import com.intellij.ui.popup.HintUpdateSupply;
import com.intellij.util.BooleanFunction;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author max
 */
public class PopupChooserBuilder<T> implements IPopupChooserBuilder<T> {

  private final PopupComponentAdapter<T> myChooserComponent;
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
  private int myVisibleRowCount = 15;

  public interface PopupComponentAdapter<T> {
    JComponent getComponent();

    default void setRenderer(ListCellRenderer renderer) {}

    void setItemChosenCallback(Consumer<T> callback);

    void setItemsChosenCallback(Consumer<Set<T>> callback);

    JScrollPane createScrollPane();

    default boolean hasOwnScrollPane() {
      return false;
    }

    default void addMouseListener(MouseListener listener) {
      getComponent().addMouseListener(listener);
    }

    default void autoSelect() {}
    default void setSelectionMode(int selection) {}

    default ListComponentUpdater getBackgroundUpdater() {
      return null;
    }

    default void setSelectedValue(T preselection, boolean shouldScroll) {
      throw new UnsupportedOperationException("Not supported for this popup type");
    }

    default void setItemSelectedCallback(Consumer<T> c) {
      throw new UnsupportedOperationException("Not supported for this popup type");
    }

    default boolean checkResetFilter() {
      return false;
    }

    @Nullable
    default BooleanFunction<KeyEvent> getKeyEventHandler() {
      return null;
    }

    default void setFont(Font f) {
      getComponent().setFont(f);
    }

    default JComponent buildFinalComponent() {
      return getComponent();
    }
  }

  @Override
  public PopupChooserBuilder<T> setCancelOnClickOutside(boolean cancelOnClickOutside) {
    myCancelOnClickOutside = cancelOnClickOutside;
    return this;
  }

  private boolean myCancelOnClickOutside = true;

  public JScrollPane getScrollPane() {
    return myScrollPane;
  }

  private JScrollPane myScrollPane;

  public PopupChooserBuilder(@NotNull JList list) {
    myChooserComponent = JBPopupFactory.getInstance().createPopupComponentAdapter(this, list);
  }

  public PopupChooserBuilder(@NotNull JTable table) {
    myChooserComponent = JBPopupFactory.getInstance().createPopupComponentAdapter(this, table);
  }

  public PopupChooserBuilder(@NotNull JTree tree) {
    myChooserComponent = JBPopupFactory.getInstance().createPopupComponentAdapter(this, tree);
  }

  @Override
  @NotNull
  public PopupChooserBuilder<T> setTitle(@NotNull @Nls String title) {
    myTitle = title;
    return this;
  }

  @NotNull
  public PopupChooserBuilder<T> addAdditionalChooseKeystroke(@Nullable KeyStroke keyStroke) {
    if (keyStroke != null) {
      myAdditionalKeystrokes.add(keyStroke);
    }
    return this;
  }

  @Override
  public IPopupChooserBuilder<T> setRenderer(ListCellRenderer renderer) {
    myChooserComponent.setRenderer(renderer);
    return this;
  }

  public JComponent getChooserComponent() {
    return myChooserComponent.getComponent();
  }

  @NotNull
  @Override
  public IPopupChooserBuilder<T> setItemChosenCallback(@NotNull Consumer<T> callback) {
    myChooserComponent.setItemChosenCallback(callback);
    return this;
  }

  @NotNull
  @Override
  public IPopupChooserBuilder<T> setItemsChosenCallback(@NotNull Consumer<Set<T>> callback) {
    myChooserComponent.setItemsChosenCallback(callback);
    return this;
  }

  @NotNull
  public PopupChooserBuilder<T> setItemChoosenCallback(@NotNull Runnable runnable) {
    myItemChosenRunnable = runnable;
    return this;
  }

  @NotNull
  public PopupChooserBuilder<T> setSouthComponent(@NotNull JComponent cmp) {
    mySouthComponent = cmp;
    return this;
  }

  @Override
  @NotNull
  public PopupChooserBuilder<T> setCouldPin(@Nullable Processor<JBPopup> callback){
    myCouldPin = callback;
    return this;
  }
  
  @NotNull
  public PopupChooserBuilder<T> setEastComponent(@NotNull JComponent cmp) {
    myEastComponent = cmp;
    return this;
  }


  @Override
  public PopupChooserBuilder<T> setRequestFocus(final boolean requestFocus) {
    myRequestFocus = requestFocus;
    return this;
  }

  @Override
  public PopupChooserBuilder<T> setResizable(final boolean forceResizable) {
    myForceResizable = forceResizable;
    return this;
  }


  @Override
  public PopupChooserBuilder<T> setMovable(final boolean forceMovable) {
    myForceMovable = forceMovable;
    return this;
  }

  @Override
  public PopupChooserBuilder<T> setDimensionServiceKey(@NonNls String key){
    myDimensionServiceKey = key;
    return this;
  }

  @Override
  public PopupChooserBuilder<T> setUseDimensionServiceForXYLocation(boolean use) {
    myUseForXYLocation = use;
    return this;
  }

  @Override
  public PopupChooserBuilder<T> setCancelCallback(Computable<Boolean> callback) {
    addCancelCallback(callback);
    return this;
  }

  public PopupChooserBuilder<T> setCommandButton(@NotNull ActiveComponent commandButton) {
    myCommandButton = commandButton;
    return this;
  }

  @Override
  public PopupChooserBuilder<T> setAlpha(final float alpha) {
    myAlpha = alpha;
    return this;
  }

  @Override
  public PopupChooserBuilder<T> setAutoselectOnMouseMove(final boolean doAutoSelect) {
    myAutoselectOnMouseMove = doAutoSelect;
    return this;
  }

  public boolean isAutoselectOnMouseMove() {
    return myAutoselectOnMouseMove;
  }

  public PopupChooserBuilder<T> setFilteringEnabled(Function<Object, String> namer) {
    myItemsNamer = namer;
    return this;
  }


  @Override
  public PopupChooserBuilder<T> setNamerForFiltering(Function<T, String> namer) {
    myItemsNamer = (Function<Object, String>)namer;
    return this;
  }

  public Function<Object, String> getItemsNamer() {
    return myItemsNamer;
  }

  @Override
  public PopupChooserBuilder<T> setModalContext(boolean modalContext) {
    myModalContext = modalContext;
    return this;
  }

  @Override
  @NotNull
  public JBPopup createPopup() {
    JPanel contentPane = new JPanel(new BorderLayout());
    if (!myForceMovable && myTitle != null) {
      JLabel label = new JLabel(myTitle);
      label.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
      label.setHorizontalAlignment(SwingConstants.CENTER);
      contentPane.add(label, BorderLayout.NORTH);
    }

    if (myAutoselect) {
      myChooserComponent.autoSelect();
    }

    myChooserComponent.addMouseListener(new MouseAdapter() {
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

    registerClosePopupKeyboardAction(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), false);
    if (myCloseOnEnter) {
      registerClosePopupKeyboardAction(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), true);
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

    JComponent finalComponent = myChooserComponent.buildFinalComponent();
    myScrollPane = myChooserComponent.createScrollPane();

    myScrollPane.getViewport().setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    Insets viewportPadding = UIUtil.getListViewportPadding();
    ((JComponent)myScrollPane.getViewport().getView()).setBorder(BorderFactory.createEmptyBorder(viewportPadding.top, viewportPadding.left, viewportPadding.bottom, viewportPadding.right));

    if (myChooserComponent.hasOwnScrollPane()) {
      addCenterComponentToContentPane(contentPane, finalComponent);
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

    ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(contentPane, finalComponent);
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

    BooleanFunction<KeyEvent> keyEventHandler = myChooserComponent.getKeyEventHandler();
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


  @Override
  public PopupChooserBuilder<T> setMinSize(final Dimension dimension) {
    myMinSize = dimension;
    return this;
  }

  @Override
  public PopupChooserBuilder<T> registerKeyboardAction(KeyStroke keyStroke, ActionListener actionListener) {
    myKeyboardActions.add(Pair.create(actionListener, keyStroke));
    return this;
  }

  private void registerClosePopupKeyboardAction(final KeyStroke keyStroke, final boolean shouldPerformAction) {
    registerPopupKeyboardAction(keyStroke, new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        if (!shouldPerformAction && myChooserComponent.checkResetFilter()) return;
        closePopup(shouldPerformAction, null, shouldPerformAction);
      }
    });
  }

  private void registerPopupKeyboardAction(final KeyStroke keyStroke, AbstractAction action) {
    myChooserComponent.getComponent().registerKeyboardAction(action, keyStroke, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
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

  @Override
  public PopupChooserBuilder<T> setAutoSelectIfEmpty(final boolean autoselect) {
    myAutoselect = autoselect;
    return this;
  }

  @Override
  public PopupChooserBuilder<T> setCancelKeyEnabled(final boolean enabled) {
    myCancelKeyEnabled = enabled;
    return this;
  }

  @Override
  public PopupChooserBuilder<T> addListener(final JBPopupListener listener) {
    myListeners.add(listener);
    return this;
  }

  @Override
  public PopupChooserBuilder<T> setSettingButton(Component abutton) {
    mySettingsButtons = abutton;
    return this;
  }

  @Override
  public PopupChooserBuilder<T> setMayBeParent(boolean mayBeParent) {
    myMayBeParent = mayBeParent;
    return this;
  }

  @Override
  public PopupChooserBuilder<T> setCloseOnEnter(boolean closeOnEnter) {
    myCloseOnEnter = closeOnEnter;
    return this;
  }

  @NotNull
  public PopupChooserBuilder<T> setFocusOwners(@NotNull Component[] focusOwners) {
    myFocusOwners = focusOwners;
    return this;
  }

  @Override
  @NotNull
  public PopupChooserBuilder<T> setAdText(String ad) {
    setAdText(ad, SwingUtilities.LEFT);
    return this;
  }

  @Override
  public PopupChooserBuilder<T> setAdText(String ad, int alignment) {
    myAd = ad;
    myAdAlignment = alignment;
    return this;
  }

  @Override
  public PopupChooserBuilder<T> setCancelOnWindowDeactivation(boolean cancelOnWindowDeactivation) {
    myCancelOnWindowDeactivation = cancelOnWindowDeactivation;
    return this;
  }

  @Override
  public IPopupChooserBuilder<T> setSelectionMode(int selection) {
    myChooserComponent.setSelectionMode(selection);
    return this;
  }

  @Override
  public IPopupChooserBuilder<T> setSelectedValue(T preselection, boolean shouldScroll) {
    myChooserComponent.setSelectedValue(preselection, shouldScroll);
    return this;
  }

  @Override
  public IPopupChooserBuilder<T> setAccessibleName(String title) {
    AccessibleContextUtil.setName(myChooserComponent.getComponent(), title);
    return this;
  }

  @Override
  public IPopupChooserBuilder<T> setItemSelectedCallback(Consumer<T> c) {
    myChooserComponent.setItemSelectedCallback(c);
    return this;
  }

  private void addCancelCallback(Computable<Boolean> cbb) {
    Computable<Boolean> callback = myCancelCallback;
    myCancelCallback = () -> cbb.compute() && (callback == null || callback.compute());
  }

  @Override
  public IPopupChooserBuilder<T> withHintUpdateSupply() {
    HintUpdateSupply.installSimpleHintUpdateSupply(myChooserComponent.getComponent());
    addCancelCallback(() -> {
      HintUpdateSupply.hideHint(myChooserComponent.getComponent());
      return true;
    });
    return this;
  }

  @Override
  public IPopupChooserBuilder<T> setFont(Font f) {
    myChooserComponent.setFont(f);
    return this;
  }

  @Override
  public IPopupChooserBuilder<T> setVisibleRowCount(int visibleRowCount) {
    myVisibleRowCount = visibleRowCount;
    return this;
  }

  public int getVisibleRowCount() {
    return myVisibleRowCount;
  }

  @Override
  public ListComponentUpdater getBackgroundUpdater() {
    return myChooserComponent.getBackgroundUpdater();
  }
}
