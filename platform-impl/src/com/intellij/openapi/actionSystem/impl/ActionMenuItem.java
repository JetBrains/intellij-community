package com.intellij.openapi.actionSystem.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.plaf.beg.BegMenuItemUI;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashSet;
import java.util.Set;

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

  public void removeNotify() {
    uninstallSynchronizer();
    super.removeNotify();
  }

  private void installSynchronizer() {
    if (myMenuItemSynchronizer == null) {
      myMenuItemSynchronizer = new MenuItemSynchronizer();
    }
  }

  private void uninstallSynchronizer() {
    if (myMenuItemSynchronizer != null) {
      Disposer.dispose(myMenuItemSynchronizer);
      myMenuItemSynchronizer = null;
    }
  }

  private void init() {
    setVisible(myPresentation.isVisible());
    setEnabled(myPresentation.isEnabled());
    setMnemonic(myPresentation.getMnemonic());
    setText(myPresentation.getText());
    final int mnemonicIndex = myPresentation.getDisplayedMnemonicIndex();

    if (getText() != null && mnemonicIndex >= 0 && mnemonicIndex < getText().length()) {
      setDisplayedMnemonicIndex(mnemonicIndex);
    }

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
    if (UIUtil.isWinLafOnVista()) {
      super.updateUI();
    }
    else {
      setUI(BegMenuItemUI.createUI(this));
    }
  }

  /**
   * Updates long description of action at the status bar.
   */
  public void menuSelectionChanged(boolean isIncluded) {
    super.menuSelectionChanged(isIncluded);
    ActionMenu.showDescriptionInStatusBar(isIncluded, this, myPresentation.getDescription());
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
        Component component = ((Component)event.getDataContext().getData(DataConstants.CONTEXT_COMPONENT));
        if (component != null && !isInTree(component)) {
          return;
        }
        myAction.actionPerformed(event);
        actionManager.queueActionPerformedEvent(myAction, myContext);
      }
    }
  }

  private void updateIcon() {
    if (myAction instanceof Toggleable && myPresentation.getIcon() == null) {
      myAction.update(myEvent);
      if (Boolean.TRUE.equals(myEvent.getPresentation().getClientProperty(Toggleable.SELECTED_PROPERTY))) {
        setIcon(ourCheckedIcon);
        setDisabledIcon(IconLoader.getDisabledIcon(ourCheckedIcon));
      }
      else {
        setIcon(ourUncheckedIcon);
        setDisabledIcon(IconLoader.getDisabledIcon(ourUncheckedIcon));
      }
    }
    else {
      if (!SystemInfo.isMac || UISettings.getInstance().SHOW_ICONS_IN_MENUS) {
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
  }

  private final class MenuItemSynchronizer implements PropertyChangeListener, Disposable {
    @NonNls private static final String SELECTED = "selected";

    private Set<String> mySynchronized = new HashSet<String>();

    private MenuItemSynchronizer() {
      myPresentation.addPropertyChangeListener(this);
    }

    public void dispose() {
      myPresentation.removePropertyChangeListener(this);
    }

    public void propertyChange(PropertyChangeEvent e) {
      boolean queueForDispose = getParent() == null;

      String name = e.getPropertyName();
      if (mySynchronized.contains(name)) return;

      mySynchronized.add(name);

      try {
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
        else if (Presentation.PROP_MNEMONIC_INDEX.equals(name)) {
          ActionMenuItem.this.setDisplayedMnemonicIndex(myPresentation.getDisplayedMnemonicIndex());
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
      finally {
        mySynchronized.remove(name);
        if (queueForDispose) {
          // later since we cannot remove property listeners inside event processing
          //noinspection SSBasedInspection
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              if (getParent() == null) {
                uninstallSynchronizer();  
              }
            }
          });
        }
      }
    }

  }
}
