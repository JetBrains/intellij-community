// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.client.ClientSystemInfo;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.util.IconUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.function.Supplier;

/**
 * @author Konstantin Bulenkov
 */
public final class CommonActionsPanel extends JPanel {
  private final ActionToolbarPosition myPosition;
  private final ActionToolbar myToolbar;

  public enum Buttons {
    ADD(AllIcons.General.Add, UIBundle.messagePointer("button.text.add")) {
      @Override
      @NotNull AnActionButton createButton(@NotNull Listener listener, String name, @NotNull Icon icon) {
        return new AddButton(listener, name == null ? getText() : name, icon);
      }
    },
    REMOVE(AllIcons.General.Remove, UIBundle.messagePointer("button.text.remove")) {
      @Override
      @NotNull AnActionButton createButton(@NotNull Listener listener, String name, @NotNull Icon icon) {
        return new RemoveButton(listener, name == null ? getText() : name, icon);
      }
    },
    EDIT(AllIcons.Actions.Edit, UIBundle.messagePointer("button.text.edit")) {
      @Override
      @NotNull AnActionButton createButton(@NotNull Listener listener, String name, @NotNull Icon icon) {
        return new EditButton(listener, name == null ? getText() : name, icon);
      }
    },
    UP(IconUtil.getMoveUpIcon(), UIBundle.messagePointer("button.text.up")) {
      @Override
      @NotNull AnActionButton createButton(@NotNull Listener listener, String name, @NotNull Icon icon) {
        return new UpButton(listener, name == null ? getText() : name, icon);
      }
    },
    DOWN(IconUtil.getMoveDownIcon(), UIBundle.messagePointer("button.text.down")) {
      @Override
      @NotNull AnActionButton createButton(@NotNull Listener listener, String name, @NotNull Icon icon) {
        return new DownButton(listener, name == null ? getText() : name, icon);
      }
    };

    private final Icon myIcon;
    private final @NotNull Supplier<@NlsContexts.Button String> myText;

    Buttons(@NotNull Icon icon, @NotNull Supplier<@NlsContexts.Button String> text) {
      myIcon = icon;
      myText = text;
    }

    public @NotNull Icon getIcon() {
      return myIcon;
    }

    abstract @NotNull AnActionButton createButton(@NotNull Listener listener, @NlsContexts.Button String name, @NotNull Icon icon);

    public @NotNull @NlsContexts.Button String getText() {
      return myText.get();
    }
  }

  public interface Listener {
    default void doAdd() {
    }

    default void doRemove() {
    }

    default void doUp() {
    }

    default void doDown() {
    }

    default void doEdit() {
    }
  }

  private final Map<Buttons, AnAction> myButtons = new HashMap<>();
  private final AnAction[] myActions;
  private EnumMap<Buttons, ShortcutSet> myCustomShortcuts;

  CommonActionsPanel(@NotNull ListenerFactory factory, @Nullable JComponent contextComponent, ActionToolbarPosition position,
                     AnAction @Nullable [] additionalActions, @Nullable Comparator<? super AnAction> buttonComparator,
                     @NlsContexts.Button String addName, @NlsContexts.Button String removeName,
                     @NlsContexts.Button String moveUpName, @NlsContexts.Button String moveDownName, @NlsContexts.Button String editName,
                     Icon addIcon, Buttons @NotNull ... buttons) {
    super(new BorderLayout());
    myPosition = position;
    final Listener listener = factory.createListener(this);
    AnAction[] actions = new AnAction[buttons.length + (additionalActions == null ? 0 : additionalActions.length)];
    for (int i = 0; i < buttons.length; i++) {
      Buttons button = buttons[i];
      String name = switch (button) {
        case ADD -> addName;
        case EDIT -> editName;
        case REMOVE -> removeName;
        case UP -> moveUpName;
        case DOWN -> moveDownName;
      };
      AnActionButton b = button.createButton(listener, name, button == Buttons.ADD && addIcon != null ? addIcon : button.getIcon());
      actions[i] = b;
      myButtons.put(button, b);
    }
    if (additionalActions != null && additionalActions.length > 0) {
      int i = buttons.length;
      for (AnAction button : additionalActions) {
        actions[i++] = button;
      }
    }
    myActions = actions;
    for (AnAction action : actions) {
      if (action instanceof AnActionButton anActionButton) {
        anActionButton.setContextComponent(contextComponent);
      }
    }
    if (buttonComparator != null) {
      Arrays.sort(myActions, buttonComparator);
    }
    AnAction[] toolbarActions = actions.clone();
    for (int i = 0; i < toolbarActions.length; i++) {
      if (toolbarActions[i] instanceof AnActionButton.CheckedAnActionButton) {
        toolbarActions[i] = ((AnActionButton.CheckedAnActionButton)toolbarActions[i]).getDelegate();
      }
    }

    ActionManager actionManager = ActionManager.getInstance();
    myToolbar = actionManager.createActionToolbar(ActionPlaces.TOOLBAR_DECORATOR_TOOLBAR,
                                                  new DefaultActionGroup(toolbarActions),
                                                  position == ActionToolbarPosition.BOTTOM || position == ActionToolbarPosition.TOP);
    myToolbar.setTargetComponent(contextComponent);
    myToolbar.getComponent().setBorder(null);
    add(myToolbar.getComponent(), BorderLayout.CENTER);
  }

  public @NotNull ActionToolbar getToolbar() {
    return myToolbar;
  }

  public void setToolbarLabel(JComponent label, ActionToolbarPosition position) {
    removeAll();
    add(label, ToolbarDecorator.getPlacement(position));
    if (position == ActionToolbarPosition.LEFT) add(myToolbar.getComponent(), BorderLayout.EAST);
    else if (position == ActionToolbarPosition.RIGHT) add(myToolbar.getComponent(), BorderLayout.WEST);
    else add(myToolbar.getComponent(), BorderLayout.CENTER);
  }

  /**
   * Returns the AnActionButton corresponding to the given button, if any.
   * @deprecated returns {@code null} if an ordinary {@code AnAction} corresponds to the given button, use {@link #getAnAction(Buttons)} instead
   * @param button one of the standard buttons
   * @return the {@code AnActionButton} if the corresponding action exists and is an instance of {@code AnActionButton}
   */
  @Deprecated
  public @Nullable AnActionButton getAnActionButton(@NotNull Buttons button) {
    return ObjectUtils.tryCast(myButtons.get(button), AnActionButton.class);
  }

  /**
   * Returns the AnAction corresponding to the given button, if any.
   * @param button one of the standard buttons
   * @return the {@code AnAction} if the corresponding action exists
   */
  public @Nullable AnAction getAnAction(@NotNull Buttons button) {
    return myButtons.get(button);
  }

  @Override
  public void addNotify() {
    if (getBackground() != null && !getBackground().equals(UIUtil.getPanelBackground())) {
      SwingUtilities.updateComponentTreeUI(this.getParent());
    }
    for (AnAction button : myActions) {
      ShortcutSet shortcut;
      if (button instanceof AnActionButton anActionButton) {
        shortcut = anActionButton.getShortcut();
      } else {
        shortcut = button.getShortcutSet();
      }
      if (shortcut != null) {
        if (button instanceof MyActionButton && myCustomShortcuts != null) {
          ShortcutSet customShortCut = myCustomShortcuts.get(((MyActionButton)button).myButton);
          if (customShortCut != null) {
            shortcut = customShortCut;
          }
        }
        button.registerCustomShortcutSet(shortcut, myToolbar.getTargetComponent());
        if (button instanceof RemoveButton) {
          registerDeleteHook((MyActionButton)button);
        }
      }
    }

    super.addNotify(); // call after all to construct actions tooltips properly
  }

  private static void registerDeleteHook(final MyActionButton removeButton) {
    new AnAction(IdeBundle.messagePointer("action.Anonymous.text.delete.hook")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        removeButton.actionPerformed(e);
      }

      @Override
      public boolean isDumbAware() {
        return removeButton.isDumbAware();
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        final JComponent contextComponent = removeButton.getContextComponent();
        if (contextComponent instanceof JTable && ((JTable)contextComponent).isEditing()) {
          e.getPresentation().setEnabled(false);
          return;
        }
        final SpeedSearchSupply supply = SpeedSearchSupply.getSupply(contextComponent);
        if (supply != null && supply.isPopupActive()) {
          e.getPresentation().setEnabled(false);
          return;
        }
        removeButton.update(e);
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }
    }.registerCustomShortcutSet(CustomShortcutSet.fromString("DELETE", "BACK_SPACE"), removeButton.getContextComponent());
  }

  public void setEnabled(Buttons button, boolean enabled) {
    AnAction b = myButtons.get(button);
    if (b instanceof AnActionButton anActionButton) {
      anActionButton.setEnabled(enabled);
    }
  }

  public void setCustomShortcuts(@NotNull Buttons button, ShortcutSet @Nullable ... shortcutSets) {
    if (shortcutSets != null) {
      if (myCustomShortcuts == null) myCustomShortcuts = new EnumMap<>(Buttons.class);
      myCustomShortcuts.put(button, new CompositeShortcutSet(shortcutSets));
    } else {
      if (myCustomShortcuts != null) {
        myCustomShortcuts.remove(button);
        if (myCustomShortcuts.isEmpty()) {
          myCustomShortcuts = null;
        }
      }
    }
  }

  public @NotNull ActionToolbarPosition getPosition() {
    return myPosition;
  }

  /**
   * Tries to calculate the 'under the toolbar button' position for a given action.
   *
   * @return the recommended popup position or null in case no toolbar button corresponds to the given action
   */
  public @Nullable RelativePoint getPreferredPopupPoint(@NotNull AnAction action) {
    return computePreferredPopupPoint(getToolbar().getComponent(), action);
  }

  @ApiStatus.Internal
  public static RelativePoint getPreferredPopupPoint(@NotNull AnAction action, @Nullable Component contextComponent) {
    var c = contextComponent;
    ActionToolbar toolbar = contextComponent instanceof ActionToolbar o ? o : null;
    while (toolbar == null && c != null && (c = c.getParent()) != null) {
      if (c instanceof JComponent o) {
        toolbar = (ActionToolbar)o.getClientProperty(ActionToolbar.ACTION_TOOLBAR_PROPERTY_KEY);
      }
    }

    if (toolbar != null) {
      RelativePoint preferredPoint = computePreferredPopupPoint(toolbar.getComponent(), action);
      if (preferredPoint != null) return preferredPoint;
    }

    return null;
  }

  static @Nullable RelativePoint computePreferredPopupPoint(@NotNull JComponent toolbar, @NotNull AnAction action) {
    for (Component comp : toolbar.getComponents()) {
      AnAction componentAction = comp instanceof AnActionHolder o ? o.getAction() :
                                 comp instanceof JComponent o ? ClientProperty.get(o, CustomComponentAction.ACTION_KEY) : null;
      if (componentAction == action ||
          (componentAction instanceof ActionWithDelegate<?> && ((ActionWithDelegate<?>)componentAction).getDelegate() == action)) {
        return new RelativePoint(comp.getParent(), new Point(comp.getX(), comp.getY() + comp.getHeight()));
      }
    }
    return null;
  }

  abstract static class MyActionButton extends AnActionButton implements DumbAware {
    private final Buttons myButton;
    protected final Listener myListener;

    MyActionButton(@NotNull Buttons button, @NotNull Listener listener, @NotNull @NlsContexts.Button String name, @NotNull Icon icon) {
      super(name, name, icon);
      myButton = button;
      myListener = listener;
    }

    @Override
    public ShortcutSet getShortcut() {
      return getCommonShortcut(myButton);
    }

    @Override
    public final void updateButton(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(
        isEnabled() &&
        isContextComponentShowingAndEnabled() &&
        isContextComponentStateAllowingAction() &&
        isEventAllowingAction(e)
      );
    }

    private boolean isContextComponentShowingAndEnabled() {
      final var component = getContextComponent();
      return component != null && component.isShowing() && component.isEnabled();
    }

    private boolean isContextComponentStateAllowingAction() {
      final var c = getContextComponent();
      if (c instanceof JTable || c instanceof JList) {
        final ListSelectionModel model = c instanceof JTable ? ((JTable)c).getSelectionModel() : ((JList<?>)c).getSelectionModel();
        final int size = c instanceof JTable ? ((JTable)c).getRowCount() : ((JList<?>)c).getModel().getSize();
        final int min = model.getMinSelectionIndex();
        final int max = model.getMaxSelectionIndex();
        return isEnabled(size, min, max);
      } else {
        return true;
      }
    }

    protected boolean isEventAllowingAction(AnActionEvent e) {
      return true;
    }

    @Override
    public final @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    protected abstract boolean isEnabled(int size, int min, int max);
  }

  static class AddButton extends MyActionButton {
    AddButton(Listener listener, @NlsContexts.Button String name, Icon icon) {
      super(Buttons.ADD, listener, name, icon);
    }

    @Override
    protected boolean isEnabled(int size, int min, int max) {
      return true;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myListener.doAdd();
    }
  }

  static class RemoveButton extends MyActionButton {
    RemoveButton(Listener listener, @NlsContexts.Button String name, Icon icon) {
      super(Buttons.REMOVE, listener, name, icon);
    }

    @Override
    protected boolean isEnabled(int size, int min, int max) {
      return size > 0;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myListener.doRemove();
    }
  }

  static class EditButton extends MyActionButton {
    EditButton(Listener listener, @NlsContexts.Button String name, Icon icon) {
      super(Buttons.EDIT, listener, name, icon);
    }

    @Override
    protected boolean isEventAllowingAction(AnActionEvent e) {
      final var c = getContextComponent();
      final var inputEvent = e.getInputEvent();
      if (
        inputEvent instanceof KeyEvent &&
        c instanceof JTable &&
        ((JTable)c).isEditing() &&
        !(inputEvent.getComponent() instanceof ActionButtonComponent) // action button active in any case in the toolbar
      ) {
        return false;
      } else {
        return true;
      }
    }

    @Override
    protected boolean isEnabled(int size, int min, int max) {
      return size > 0 && min == max && min >= 0;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myListener.doEdit();
    }
  }

  static class UpButton extends MyActionButton {
    UpButton(Listener listener, @NlsContexts.Button String name, Icon icon) {
      super(Buttons.UP, listener, name, icon);
    }

    @Override
    protected boolean isEnabled(int size, int min, int max) {
      return size > 0 && min >= 1;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myListener.doUp();
    }
  }

  static class DownButton extends MyActionButton {
    DownButton(Listener listener, @NlsContexts.Button String name, Icon icon) {
      super(Buttons.DOWN, listener, name, icon);
    }

    @Override
    protected boolean isEnabled(int size, int min, int max) {
      return size > 0 && max < size-1;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myListener.doDown();
    }
  }

  public static @NotNull ShortcutSet getCommonShortcut(@NotNull Buttons button) {
    return switch (button) {
      case ADD -> CommonShortcuts.getNewForDialogs();
      case EDIT -> CustomShortcutSet.fromString("ENTER");
      case REMOVE -> CustomShortcutSet.fromString(ClientSystemInfo.isMac() ? "meta BACK_SPACE" : "alt DELETE");
      case UP -> CommonShortcuts.MOVE_UP;
      case DOWN -> CommonShortcuts.MOVE_DOWN;
    };
  }

  interface ListenerFactory {
    @NotNull
    Listener createListener(@NotNull CommonActionsPanel panel);
  }
}
