package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.impl.IdeFrame;
import com.intellij.ui.plaf.beg.BegMenuItemUI;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class ActionMenuItem extends JMenuItem {
  private static final Icon ourCheckedIcon = IconLoader.getIcon("/actions/check.png");
  private static final Icon ourUncheckedIcon = new EmptyIcon(18, 18);

  private final AnAction myAction;
  private final Presentation myPresentation;
  private final String myPlace;
  private final DataContext myContext;
  private final AnActionEvent myEvent;
  private MenuItemSynchronizer myMenuItemSynchronizer;

  public ActionMenuItem(AnAction action, Presentation presentation, String place, DataContext context) {
    myAction = action;
    myPresentation = presentation;
    myPlace = place;
    myContext = context;
    myEvent = new AnActionEvent(null, context, place, myPresentation, ActionManager.getInstance(), 0);
    addActionListener(new ActionTransmitter());
    setBorderPainted(false);
    init();

    if (SystemInfo.isMacSystemMenu) {
      // Menu items in MacOS system menu won't get addNotify() called. :(
      installSynchronizer();
    }
  }

  /**
   * We have to make this method public to allow BegMenuItemUI to invoke it.
   */
  public void fireActionPerformed(ActionEvent event) {
    super.fireActionPerformed(event);
  }

  public void addNotify() {
    super.addNotify();
    installSynchronizer();
    init();
  }

  private void installSynchronizer() {
    if (myMenuItemSynchronizer == null) {
      myMenuItemSynchronizer = new MenuItemSynchronizer();
      myPresentation.addPropertyChangeListener(myMenuItemSynchronizer);
    }
  }

  public void removeNotify() {
    if (myMenuItemSynchronizer != null) {
      myPresentation.removePropertyChangeListener(myMenuItemSynchronizer);
      myMenuItemSynchronizer = null;
    }
    super.removeNotify();
  }

  private void init() {
    setVisible(myPresentation.isVisible());
    setEnabled(myPresentation.isEnabled());
    setMnemonic(myPresentation.getMnemonic());
    setText(myPresentation.getText());
    updateIcon();
    String id = ActionManager.getInstance().getId(myAction);
    if (id != null) {
      Shortcut[] shortcuts = KeymapManager.getInstance().getActiveKeymap().getShortcuts(id);
      for (Shortcut shortcut : shortcuts) {
        if (shortcut instanceof KeyboardShortcut) {
          setAccelerator(((KeyboardShortcut)shortcut).getFirstKeyStroke());
          break;
        }
      }
    }
  }

  public void updateUI() {
    setUI(BegMenuItemUI.createUI(this));
  }

  /**
   * Updates long description of action at the status bar.
   */
  public void menuSelectionChanged(boolean isIncluded) {
    super.menuSelectionChanged(isIncluded);
    IdeFrame frame = (IdeFrame)SwingUtilities.getAncestorOfClass(IdeFrame.class, this);
    if (frame != null) {
      StatusBar statusBar = frame.getStatusBar();
      if (isIncluded) {
        statusBar.setInfo(myPresentation.getDescription());
      }
      else {
        statusBar.setInfo(null);
      }
    }
  }

  public String getFirstShortcutText() {
    return KeymapUtil.getFirstKeyboardShortcutText(myAction);
  }

  private final class ActionTransmitter implements ActionListener {
    /**
     * @return whether the component in Swing tree or not. This method is more
     *         weak then {@link Component#isShowing() }
     */
    private boolean isInTree(final Component component) {
      if (component instanceof Window) {
        return component.isShowing();
      }
      else {
        Window windowAncestor = SwingUtilities.getWindowAncestor(component);
        return windowAncestor != null && windowAncestor.isShowing();
      }
    }

    public void actionPerformed(final ActionEvent e) {
      AnActionEvent event = new AnActionEvent(
        new MouseEvent(ActionMenuItem.this, MouseEvent.MOUSE_PRESSED, 0, e.getModifiers(), getWidth() / 2, getHeight() / 2, 1, false),
        myContext, myPlace, myPresentation, ActionManager.getInstance(), e.getModifiers());
      myAction.beforeActionPerformedUpdate(event);
      if (myPresentation.isEnabled()) {
        ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
        actionManager.fireBeforeActionPerformed(myAction, myContext);
        Component component = ((Component)event.getDataContext().getData(DataConstantsEx.CONTEXT_COMPONENT));
        if (component != null && !isInTree(component)) {
          return;
        }
        myAction.actionPerformed(event);
      }
    }
  }

  private void updateIcon() {
    if (myAction instanceof ToggleAction && myPresentation.getIcon() == null) {
      ToggleAction stateAction = (ToggleAction)myAction;
      if (stateAction.isSelected(myEvent)) {
        setIcon(ourCheckedIcon);
        setDisabledIcon(IconLoader.getDisabledIcon(ourCheckedIcon));
      }
      else {
        setIcon(ourUncheckedIcon);
        setDisabledIcon(IconLoader.getDisabledIcon(ourUncheckedIcon));
      }
    }
    else {
      Icon icon = myPresentation.getIcon();
      setIcon(icon);
      if (myPresentation.getDisabledIcon() != null) {
        setDisabledIcon(myPresentation.getDisabledIcon());
      }
      else {
        setDisabledIcon(IconLoader.getDisabledIcon(icon));
      }
    }
  }

  private final class MenuItemSynchronizer implements PropertyChangeListener {
    @NonNls private static final String SELECTED = "selected";

    public void propertyChange(PropertyChangeEvent e) {
      String name = e.getPropertyName();
      if (Presentation.PROP_VISIBLE.equals(name)) {
        final boolean visible = myPresentation.isVisible();
        if (!visible && SystemInfo.isMacSystemMenu && myPlace == ActionPlaces.MAIN_MENU) {
          ActionMenuItem.this.setEnabled(false);
        }
        else {
          ActionMenuItem.this.setVisible(visible);
        }
      }
      else if (Presentation.PROP_ENABLED.equals(name)) {
        ActionMenuItem.this.setEnabled(myPresentation.isEnabled());
        updateIcon();
      }
      else if (Presentation.PROP_MNEMONIC_KEY.equals(name)) {
        ActionMenuItem.this.setMnemonic(myPresentation.getMnemonic());
      }
      else if (Presentation.PROP_TEXT.equals(name)) {
        ActionMenuItem.this.setText(myPresentation.getText());
      }
      else if (Presentation.PROP_ICON.equals(name) || Presentation.PROP_DISABLED_ICON.equals(name)) {
        updateIcon();
      }
      else if (SELECTED.equals(name)) {
        updateIcon();
      }
    }
  }
}
