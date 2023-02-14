// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.popup;

import com.intellij.openapi.ui.GenericListComponentUpdater;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsContexts.PopupTitle;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ActiveComponent;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.popup.HintUpdateSupply;
import com.intellij.ui.speedSearch.ListWithFilter;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public class PopupChooserBuilder<T> implements IPopupChooserBuilder<T> {
  private final PopupComponentAdapter<T> myChooserComponent;
  private @PopupTitle String myTitle;
  private final ArrayList<KeyStroke> myAdditionalKeystrokes = new ArrayList<>();
  private Runnable myItemChosenRunnable;
  private JBSplitter myContentSplitter;
  private JComponent myNorthComponent;
  private JComponent mySouthComponent;
  private JComponent myEastComponent;
  private JComponent myPreferableFocusComponent;

  private JBPopup myPopup;

  private boolean myRequestFocus = true;
  private boolean myForceResizable;
  private boolean myForceMovable;
  private String myDimensionServiceKey;
  private Computable<Boolean> myCancelCallback;
  private boolean myAutoselect = true;
  private float myAlpha;
  private Component[] myFocusOwners = new Component[0];
  private boolean myCancelKeyEnabled = true;

  private final List<JBPopupListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private @NlsContexts.PopupAdvertisement String myAd;
  private JComponent myAdvertiser;

  private Dimension myMinSize;
  private ActiveComponent myCommandButton;
  private final List<Pair<ActionListener,KeyStroke>> myKeyboardActions = new ArrayList<>();
  private Component mySettingsButtons;
  private boolean myAutoselectOnMouseMove = true;

  private Function<? super T, String> myItemsNamer;
  private boolean myFilterAlwaysVisible;
  private boolean myMayBeParent;
  private int myAdAlignment = SwingConstants.LEFT;
  private boolean myModalContext;
  private boolean myCloseOnEnter = true;
  private boolean myCancelOnWindowDeactivation = true;
  private boolean myCancelOnOtherWindowOpen = true;
  private boolean myUseForXYLocation;
  private @Nullable Processor<? super JBPopup> myCouldPin;
  private int myVisibleRowCount = 15;
  private boolean myAutoPackHeightOnFiltering = true;

  public interface PopupComponentAdapter<T> {
    JComponent getComponent();

    default void setRenderer(ListCellRenderer<? super T> renderer) {}

    void setItemChosenCallback(Consumer<? super T> callback);

    void setItemsChosenCallback(Consumer<? super Set<T>> callback);

    JScrollPane createScrollPane();

    default boolean hasOwnScrollPane() {
      return false;
    }

    default void addMouseListener(MouseListener listener) {
      getComponent().addMouseListener(listener);
    }

    default void autoSelect() {}
    default void setSelectionMode(int selection) {}

    default GenericListComponentUpdater<T> getBackgroundUpdater() {
      return null;
    }

    default void setSelectedValue(T preselection, boolean shouldScroll) {
      throw new UnsupportedOperationException("Not supported for this popup type");
    }

    default void setItemSelectedCallback(Consumer<? super T> c) {
      throw new UnsupportedOperationException("Not supported for this popup type");
    }

    default boolean checkResetFilter() {
      return false;
    }

    @Nullable
    default Predicate<KeyEvent> getKeyEventHandler() {
      return null;
    }

    default void setFont(Font f) {
      getComponent().setFont(f);
    }

    default JComponent buildFinalComponent() {
      return getComponent();
    }

    default void setFixedRendererSize(@NotNull Dimension dimension) {}
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

  public PopupChooserBuilder(@NotNull JList<T> list) {
    myChooserComponent = JBPopupFactory.getInstance().createPopupComponentAdapter(this, list);
  }

  public PopupChooserBuilder(@NotNull JTable table) {
    myChooserComponent = JBPopupFactory.getInstance().createPopupComponentAdapter(this, table);
  }

  public PopupChooserBuilder(@NotNull JTree tree) {
    myChooserComponent = JBPopupFactory.getInstance().createPopupComponentAdapter(this, tree);
  }

  @Override
  public PopupChooserBuilder<T> setTitle(@NotNull @PopupTitle String title) {
    myTitle = title;
    return this;
  }

  public PopupChooserBuilder<T> addAdditionalChooseKeystroke(@Nullable KeyStroke keyStroke) {
    if (keyStroke != null) {
      myAdditionalKeystrokes.add(keyStroke);
    }
    return this;
  }

  @Override
  public IPopupChooserBuilder<T> setRenderer(ListCellRenderer<? super T> renderer) {
    myChooserComponent.setRenderer(renderer);
    return this;
  }

  public JComponent getChooserComponent() {
    return myChooserComponent.getComponent();
  }

  @Override
  public IPopupChooserBuilder<T> setItemChosenCallback(@NotNull Consumer<? super T> callback) {
    myChooserComponent.setItemChosenCallback(callback);
    return this;
  }

  @Override
  public IPopupChooserBuilder<T> setItemsChosenCallback(@NotNull Consumer<? super Set<? extends T>> callback) {
    myChooserComponent.setItemsChosenCallback(callback);
    return this;
  }

  public PopupChooserBuilder<T> setItemChoosenCallback(@NotNull Runnable runnable) {
    myItemChosenRunnable = runnable;
    return this;
  }

  public PopupChooserBuilder<T> setNorthComponent(@NotNull JComponent cmp) {
    myNorthComponent = cmp;
    return this;
  }

  public PopupChooserBuilder<T> setSouthComponent(@NotNull JComponent cmp) {
    mySouthComponent = cmp;
    return this;
  }

  public PopupChooserBuilder<T> setContentSplitter(@NotNull JBSplitter splitter) {
    myContentSplitter = splitter;
    return this;
  }

  @Override
  public PopupChooserBuilder<T> setCouldPin(@Nullable Processor<? super JBPopup> callback){
    myCouldPin = callback;
    return this;
  }

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
  public PopupChooserBuilder<T> setFilterAlwaysVisible(boolean state) {
    myFilterAlwaysVisible = state;
    return this;
  }

  public boolean isFilterAlwaysVisible() {
    return myFilterAlwaysVisible;
  }

  @Override
  public PopupChooserBuilder<T> setNamerForFiltering(Function<? super T, String> namer) {
    myItemsNamer = namer;
    return this;
  }

  public Function<? super T, String> getItemsNamer() {
    return myItemsNamer;
  }

  @Override
  public IPopupChooserBuilder<T> setAutoPackHeightOnFiltering(boolean autoPackHeightOnFiltering) {
    myAutoPackHeightOnFiltering = autoPackHeightOnFiltering;
    return this;
  }

  public boolean isAutoPackHeightOnFiltering() {
    return myAutoPackHeightOnFiltering;
  }

  @Override
  public PopupChooserBuilder<T> setModalContext(boolean modalContext) {
    myModalContext = modalContext;
    return this;
  }

  public JComponent getPreferableFocusComponent() {
    return myPreferableFocusComponent;
  }

  @Override
  public @NotNull JBPopup createPopup() {
    JPanel contentPane = new JPanel(new BorderLayout());

    if (myAutoselect) {
      myChooserComponent.autoSelect();
    }

    if (myCloseOnEnter || myItemChosenRunnable != null) {
      myChooserComponent.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseReleased(MouseEvent e) {
          if (UIUtil.isActionClick(e, MouseEvent.MOUSE_RELEASED) && !UIUtil.isSelectionButtonDown(e) && !e.isConsumed()) {
            if (myCloseOnEnter) {
              closePopup(e, true);
            }
            else {
              myItemChosenRunnable.run();
            }
          }
        }
      });
    }

    registerClosePopupKeyboardAction(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), false);
    if (myCloseOnEnter) {
      registerClosePopupKeyboardAction(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), true);
    }
    else if (myItemChosenRunnable != null) {
      registerKeyboardAction(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), __ -> myItemChosenRunnable.run());
    }
    for (KeyStroke keystroke : myAdditionalKeystrokes) {
      registerClosePopupKeyboardAction(keystroke, true);
    }

    myPreferableFocusComponent = myChooserComponent.buildFinalComponent();
    myScrollPane = myChooserComponent.createScrollPane();

    myScrollPane.getViewport().setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    Insets viewportPadding = UIUtil.getListViewportPadding(StringUtil.isNotEmpty(myAd));
    ((JComponent)myScrollPane.getViewport().getView()).setBorder(
      BorderFactory.createEmptyBorder(viewportPadding.top, viewportPadding.left, viewportPadding.bottom, viewportPadding.right));

    JComponent contentComponent = myChooserComponent.hasOwnScrollPane() ? myPreferableFocusComponent : myScrollPane;

    if (myContentSplitter != null) {
      myContentSplitter.setFirstComponent(contentComponent);
      addCenterComponentToContentPane(contentPane, myContentSplitter);
    }
    else {
      addCenterComponentToContentPane(contentPane, contentComponent);
    }

    if (myNorthComponent != null) {
      addNorthComponentToContentPane(contentPane, myNorthComponent);
    }

    if (mySouthComponent != null) {
      addSouthComponentToContentPane(contentPane, mySouthComponent);
    }

    if (myEastComponent != null) {
      addEastComponentToContentPane(contentPane, myEastComponent);
    }

    if (ExperimentalUI.isNewUI()) {
      applyInsets(contentComponent);
    }

    ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(contentPane, myPreferableFocusComponent);
    for (JBPopupListener each : myListeners) {
      builder.addListener(each);
    }

    builder
      .setDimensionServiceKey(null, myDimensionServiceKey, myUseForXYLocation)
      .setRequestFocus(myRequestFocus)
      .setResizable(myForceResizable)
      .setMovable(myForceMovable)
      .setTitle(myTitle)
      .setAlpha(myAlpha)
      .setFocusOwners(myFocusOwners)
      .setCancelKeyEnabled(myCancelKeyEnabled)
      .setAdText(myAd, myAdAlignment)
      .setAdvertiser(myAdvertiser)
      .setKeyboardActions(myKeyboardActions)
      .setMayBeParent(myMayBeParent)
      .setLocateWithinScreenBounds(true)
      .setCancelOnOtherWindowOpen(myCancelOnOtherWindowOpen)
      .setModalContext(myModalContext)
      .setCancelOnWindowDeactivation(myCancelOnWindowDeactivation)
      .setCancelOnClickOutside(myCancelOnClickOutside)
      .setCouldPin(myCouldPin)
      .setOkHandler(myItemChosenRunnable);
    if (myCancelCallback != null) {
      builder.setCancelCallback(myCancelCallback);
    }
    Predicate<KeyEvent> keyEventHandler = myChooserComponent.getKeyEventHandler();
    if (keyEventHandler != null) {
      builder.setKeyEventHandler(keyEventHandler::test);
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

  private static void addEastComponentToContentPane(JPanel contentPane, JComponent component) {
    contentPane.add(component, BorderLayout.EAST);
  }

  private static void addNorthComponentToContentPane(JPanel contentPane, JComponent component) {
    contentPane.add(component, BorderLayout.NORTH);
  }

  private static void addSouthComponentToContentPane(JPanel contentPane, JComponent component) {
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
      @Override
      public void actionPerformed(ActionEvent e) {
        if (!shouldPerformAction && myChooserComponent.checkResetFilter()) return;
        closePopup(null, shouldPerformAction);
      }
    });
  }

  private void registerPopupKeyboardAction(final KeyStroke keyStroke, AbstractAction action) {
    myChooserComponent.getComponent().registerKeyboardAction(action, keyStroke, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
  }

  private void closePopup(MouseEvent e, boolean isOk) {
    if (isOk) {
      myPopup.closeOk(e);
    }
    else {
      myPopup.cancel(e);
    }
  }

  @Override
  public PopupChooserBuilder<T> setAutoSelectIfEmpty(final boolean autoSelect) {
    myAutoselect = autoSelect;
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
  public PopupChooserBuilder<T> setSettingButton(Component button) {
    mySettingsButtons = button;
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

  public PopupChooserBuilder<T> setFocusOwners(Component @NotNull [] focusOwners) {
    myFocusOwners = focusOwners;
    return this;
  }

  @Override
  public PopupChooserBuilder<T> setAdText(String ad) {
    setAdText(ad, SwingConstants.LEFT);
    return this;
  }

  @Override
  public PopupChooserBuilder<T> setAdvertiser(@Nullable JComponent advertiser) {
    myAdvertiser = advertiser;
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
  public IPopupChooserBuilder<T> setCancelOnOtherWindowOpen(boolean cancelOnWindow) {
    myCancelOnOtherWindowOpen = cancelOnWindow;
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
  public IPopupChooserBuilder<T> setItemSelectedCallback(Consumer<? super T> c) {
    myChooserComponent.setItemSelectedCallback(c);
    return this;
  }

  private void addCancelCallback(Computable<Boolean> cbb) {
    Computable<Boolean> callback = myCancelCallback;
    myCancelCallback = () -> cbb.compute() && (callback == null || callback.compute());
  }

  @Override
  public IPopupChooserBuilder<T> withHintUpdateSupply() {
    HintUpdateSupply.installDataContextHintUpdateSupply(myChooserComponent.getComponent());
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

  @Override
  public IPopupChooserBuilder<T> withFixedRendererSize(@NotNull Dimension dimension) {
    myChooserComponent.setFixedRendererSize(dimension);
    return this;
  }

  public int getVisibleRowCount() {
    return myVisibleRowCount;
  }

  @Override
  public GenericListComponentUpdater<T> getBackgroundUpdater() {
    return myChooserComponent.getBackgroundUpdater();
  }

  /**
   * Applies borders according to contentComponent. Can be extended later for different types of component
   */
  private void applyInsets(JComponent contentComponent) {
    if (contentComponent instanceof ListWithFilter<?> listWithFilter) {
      Insets insets = PopupUtil.getListInsets(StringUtil.isNotEmpty(myTitle), StringUtil.isNotEmpty(myAd));
      listWithFilter.getList().setBorder(new EmptyBorder(insets));
    }
  }
}
