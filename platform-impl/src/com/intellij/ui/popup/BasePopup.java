/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui.popup;

import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.ui.popup.*;
import com.intellij.ui.PopupBorder;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.ui.popup.tree.TreePopupImpl;
import com.intellij.ui.popup.util.ElementFilter;
import com.intellij.ui.popup.util.MnemonicsSearch;
import com.intellij.ui.popup.util.SpeedSearch;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public abstract class BasePopup extends AbstractPopup implements ActionListener, ElementFilter {

  private static final int AUTO_POPUP_DELAY = 750;
  private static final Dimension MAX_SIZE = new Dimension(Integer.MAX_VALUE, 600);

  protected static final int STEP_X_PADDING = 2;

  private final BasePopup myParent;

  protected final PopupStep<Object> myStep;
  protected BasePopup myChild;

  private JScrollPane myScrollPane;

  private final Timer myAutoSelectionTimer = new Timer(AUTO_POPUP_DELAY, this);

  private final SpeedSearch mySpeedSearch = new SpeedSearch() {
    protected void update() {
      onSpeedSearchPatternChanged();
      mySpeedSearchPane.update();
    }
  };

  private SpeedSearchPane mySpeedSearchPane;

  private MnemonicsSearch myMnemonicsSearch;
  private Object myParentValue;

  private Point myLastOwnerPoint;
  private Window myOwnerWindow;
  private MyComponentAdapter myOwnerListener;

  public BasePopup(PopupStep aStep) {
    this(null, aStep);
  }

  public BasePopup(JBPopup aParent, PopupStep aStep) {
    myParent = (BasePopup) aParent;
    myStep = aStep;

    if (myStep.isSpeedSearchEnabled() && myStep.isMnemonicsNavigationEnabled()) {
      throw new IllegalArgumentException("Cannot have both options enabled at the same time: speed search and mnemonics navigation");
    }

    mySpeedSearch.setEnabled(myStep.isSpeedSearchEnabled());

    mySpeedSearchPane = new SpeedSearchPane(this);

    final JComponent content = createContent();

    myScrollPane = new JScrollPane(content);
    myScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    myScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    myScrollPane.getHorizontalScrollBar().setBorder(null);

    myScrollPane.getActionMap().get("unitScrollLeft").setEnabled(false);
    myScrollPane.getActionMap().get("unitScrollRight").setEnabled(false);

    myScrollPane.setBorder(null);

    init(null, myScrollPane, getPreferredFocusableComponent(), true, true, true, null,
         false, aStep.getTitle(), null, true, null, false, null, null, false, null, true, false, true, null, 0f,
         null, true, false, new Component[0], null, true);

    registerAction("disposeAll", KeyEvent.VK_ESCAPE, InputEvent.SHIFT_MASK, new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        if (mySpeedSearch.isHoldingFilter()) {
          mySpeedSearch.reset();
        }
        else {
          disposeAll();
        }
      }
    });

    AbstractAction goBackAction = new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        goBack();
      }
    };

    registerAction("goBack3", KeyEvent.VK_ESCAPE, 0, goBackAction);

    myMnemonicsSearch = new MnemonicsSearch(this) {
      protected void select(Object value) {
        onSelectByMnemonic(value);
      }
    };



  }

  private void disposeAll() {
    BasePopup root = PopupDispatcher.getActiveRoot();
    disposeAllParents();
    root.getStep().canceled();
  }

  public void goBack() {
    if (mySpeedSearch.isHoldingFilter()) {
      mySpeedSearch.reset();
      return;
    }

    if (myParent != null) {
      myParent.disposeChildren();
    }
    else {
      disposeAll();
    }
  }

  protected abstract JComponent createContent();

  public void dispose() {
    super.dispose();

    myAutoSelectionTimer.stop();

    mySpeedSearchPane.dispose();

    PopupDispatcher.unsetShowing(this);
    PopupDispatcher.clearRootIfNeeded(this);


    if (myOwnerWindow != null && myOwnerListener != null) {
      myOwnerWindow.removeComponentListener(myOwnerListener);
    }
  }


  public void disposeChildren() {
    if (myChild != null) {
      myChild.disposeChildren();
      myChild.dispose();
      myChild = null;
    }
  }

  public void show(final Component owner, final int aScreenX, final int aScreenY, final boolean considerForcedXY) {
    //myScrollPane.getViewport().setPreferredSize(getContent().getPreferredSize());
    //
    Rectangle targetBounds = new Rectangle(new Point(aScreenX, aScreenY), getContent().getPreferredSize());
    ScreenUtil.moveRectangleToFitTheScreen(targetBounds);

    if (getParent() != null) {
      if (getParent().getBounds().intersects(targetBounds)) {
        targetBounds.x = getParent().getBounds().x - targetBounds.width - STEP_X_PADDING;
      }
    }

    if (getParent() == null) {
      PopupDispatcher.setActiveRoot(this);
    }
    else {
      PopupDispatcher.setShowing(this);
    }

    super.show(owner, targetBounds.x, targetBounds.y, true);
  }

  protected void afterShow() {
    super.afterShow();
    registerAutoMove();

    if (!myFocusTrackback.isMustBeShown()) {
      cancel();
    }
  }

  private void registerAutoMove() {
    if (myOwner != null) {
      myOwnerWindow = SwingUtilities.getWindowAncestor(myOwner);
      if (myOwnerWindow != null) {
        myLastOwnerPoint = myOwnerWindow.getLocationOnScreen();
        myOwnerListener = new MyComponentAdapter();
        myOwnerWindow.addComponentListener(myOwnerListener);
      }
    }
  }

  private void processParentWindowMoved() {
    if (isDisposed()) return;

    final Point newOwnerPoint = myOwnerWindow.getLocationOnScreen();

    int deltaX = myLastOwnerPoint.x - newOwnerPoint.x;
    int deltaY = myLastOwnerPoint.y - newOwnerPoint.y;

    myLastOwnerPoint = newOwnerPoint;

    final Window wnd = SwingUtilities.getWindowAncestor(getContent());
    final Point current = wnd.getLocationOnScreen();

    setLocation(new Point(current.x - deltaX, current.y - deltaY));
  }

  protected abstract JComponent getPreferredFocusableComponent();

  public void cancel() {
    super.cancel();
    disposeChildren();
    dispose();
    getStep().canceled();
  }


  protected void disposeAllParents() {
    dispose();
    if (myParent != null) {
      myParent.disposeAllParents();
    }
  }

  public final void registerAction(@NonNls String aActionName, int aKeyCode, int aModifier, Action aAction) {
    getInputMap().put(KeyStroke.getKeyStroke(aKeyCode, aModifier), aActionName);
    getActionMap().put(aActionName, aAction);
  }

  protected abstract InputMap getInputMap();

  protected abstract ActionMap getActionMap();

  protected final void setParentValue(Object parentValue) {
    myParentValue = parentValue;
  }

  @NotNull
  protected MyContentPanel createContentPanel(final boolean resizable, final PopupBorder border, final boolean isToDrawMacCorner) {
    return new MyContainer(resizable, border, isToDrawMacCorner);
  }

  private static class MyContainer extends MyContentPanel implements DataProvider {

    private MyContainer(final boolean resizable, final PopupBorder border, final boolean drawMacCorner) {
      super(resizable, border, drawMacCorner);
      setOpaque(true);
      setFocusCycleRoot(true);
    }

    public Object getData(String dataId) {
      return null;
    }

    public Dimension getPreferredSize() {
      return computeNotBiggerDimension(super.getPreferredSize());
    }

    private static Dimension computeNotBiggerDimension(Dimension ofContent) {
      int resultWidth = ofContent.width > MAX_SIZE.width ? MAX_SIZE.width : ofContent.width;
      int resultHeight = ofContent.height > MAX_SIZE.height ? MAX_SIZE.height : ofContent.height;

      return new Dimension(resultWidth, resultHeight);
    }
  }

  public BasePopup getParent() {
    return myParent;
  }

  public PopupStep getStep() {
    return myStep;
  }

  public final void dispatch(KeyEvent event) {
    if (event.getID() != KeyEvent.KEY_PRESSED) {
      return;
    }
    final KeyStroke stroke = KeyStroke.getKeyStroke(event.getKeyChar(), event.getModifiers(), false);
    if (getInputMap().get(stroke) != null) {
      final Action action = getActionMap().get(getInputMap().get(stroke));
      if (action != null && action.isEnabled()) {
        action.actionPerformed(new ActionEvent(getContent(), event.getID(), "", event.getWhen(), event.getModifiers()));
        return;
      }
    }

    process(event);
    mySpeedSearch.process(event);
    myMnemonicsSearch.process(event);
  }

  protected void process(KeyEvent aEvent) {

  }

  public Rectangle getBounds() {
    return new Rectangle(getContent().getLocationOnScreen(), getContent().getSize());
  }

  protected static BasePopup createPopup(BasePopup parent, PopupStep step, Object parentValue) {
    if (step instanceof ListPopupStep) {
      return new ListPopupImpl(parent, (ListPopupStep)step, parentValue);
    }
    else if (step instanceof TreePopupStep) {
      return new TreePopupImpl(parent, (TreePopupStep)step, parentValue);
    }
    else {
      throw new IllegalArgumentException(step.getClass().toString());
    }
  }

  public final void actionPerformed(ActionEvent e) {
    myAutoSelectionTimer.stop();
    if (getStep().isAutoSelectionEnabled()) {
      onAutoSelectionTimer();
    }
  }

  protected final void restartTimer() {
    if (!myAutoSelectionTimer.isRunning()) {
      myAutoSelectionTimer.start();
    }
    else {
      myAutoSelectionTimer.restart();
    }
  }

  protected final void stopTimer() {
    myAutoSelectionTimer.stop();
  }

  protected void onAutoSelectionTimer() {

  }

  protected void onSpeedSearchPatternChanged() {
  }

  public boolean shouldBeShowing(Object value) {
    if (!myStep.isSpeedSearchEnabled()) return true;
    SpeedSearchFilter<Object> filter = myStep.getSpeedSearchFilter();
    if (!filter.canBeHidden(value)) return true;
    String text = filter.getIndexedString(value);
    return mySpeedSearch.shouldBeShowing(text);
  }

  public SpeedSearch getSpeedSearch() {
    return mySpeedSearch;
  }


  protected void onSelectByMnemonic(Object value) {

  }

  protected abstract void onChildSelectedFor(Object value);

  protected final void notifyParentOnChildSelection() {
    if (myParent == null || myParentValue == null) return;
    myParent.onChildSelectedFor(myParentValue);
  }


  private class MyComponentAdapter extends ComponentAdapter {
    public void componentMoved(final ComponentEvent e) {
      processParentWindowMoved();
    }
  }

}
