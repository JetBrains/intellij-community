// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * @author Konstantin Bulenkov
 */
public abstract class AnActionButton extends AnAction implements ShortcutProvider {
  private static final Logger LOG = Logger.getInstance(AnActionButton.class);
  private boolean myEnabled = true;
  private boolean myVisible = true;
  private JComponent myContextComponent;
  private Set<AnActionButtonUpdater> myUpdaters;
  private final List<ActionButtonListener> myListeners = new ArrayList<>();

  public AnActionButton(@NlsContexts.Button String text) {
    super(() -> text);
  }

  public AnActionButton(@NotNull Supplier<String> dynamicText) {
    super(dynamicText);
  }

  public AnActionButton(@NlsContexts.Button String text,
                        @NlsContexts.Tooltip String description,
                        @Nullable Icon icon) {
    super(text, description, icon);
  }

  public AnActionButton(@NotNull Supplier<String> dynamicText,
                        @NotNull Supplier<String> dynamicDescription,
                        @Nullable Icon icon) {
    super(dynamicText, dynamicDescription, icon);
  }

  public AnActionButton(@NlsContexts.Button String text, Icon icon) {
    this(text, null, icon);
  }

  public AnActionButton(@NotNull Supplier<String> dynamicText, Icon icon) {
    this(dynamicText, Presentation.NULL_STRING, icon);
  }

  public AnActionButton() {
  }

  public static AnActionButton fromAction(final AnAction action) {
    final Presentation presentation = action.getTemplatePresentation();
    final AnActionButtonWrapper button = action instanceof CheckedActionGroup ? new CheckedAnActionButton(presentation, action) :
                                         action instanceof Toggleable ? new ToggleableButtonWrapper(presentation, action) :
                                         new AnActionButtonWrapper(presentation, action);
    button.setShortcut(action.getShortcutSet());
    return button;
  }

  public boolean isEnabled() {
    return myEnabled;
  }

  public void setEnabled(boolean enabled) {
    if (myEnabled != enabled) {
      myEnabled = enabled;
      myListeners.forEach(l -> l.isEnabledChanged(enabled));
    }
  }

  public boolean isVisible() {
    return myVisible;
  }

  public void setVisible(boolean visible) {
    if (myVisible != visible) {
      myVisible = visible;
      myListeners.forEach(l -> l.isVisibleChanged(visible));
    }
  }

  @Override
  public final void update(@NotNull AnActionEvent e) {
    boolean enabled = isEnabled();
    if (enabled && myUpdaters != null) {
      for (AnActionButtonUpdater updater : myUpdaters) {
        if (!updater.isEnabled(e)) {
          enabled = false;
          break;
        }
      }
    }
    e.getPresentation().setEnabled(enabled);
    e.getPresentation().setVisible(isVisible());

    if (enabled) {
      updateButton(e);
    }
  }

  public final void addCustomUpdater(@NotNull AnActionButtonUpdater updater) {
    if (myUpdaters == null) {
      myUpdaters = new HashSet<>();
    }
    myUpdaters.add(updater);
  }

  public void updateButton(@NotNull AnActionEvent e) {
  }

  @Override
  public ShortcutSet getShortcut() {
    return getShortcutSet();
  }

  public void setShortcut(@NotNull ShortcutSet shortcut) {
    setShortcutSet(shortcut);
  }

  public void setContextComponent(JComponent contextComponent) {
    myContextComponent = contextComponent;
  }

  public JComponent getContextComponent() {
    return myContextComponent;
  }

  @NotNull
  public DataContext getDataContext() {
    return DataManager.getInstance().getDataContext(getContextComponent());
  }

  @NotNull
  public final RelativePoint getPreferredPopupPoint() {
    RelativePoint result = CommonActionsPanel.getPreferredPopupPoint(this, myContextComponent);
    if (result != null) {
      return result;
    }
    LOG.error("Can't find toolbar button");
    return RelativePoint.getCenterOf(myContextComponent);
  }

  /**
   * Tries to calculate the 'under the toolbar button' position for a given action.
   *
   * @return the recommended popup position or null in case no toolbar button corresponds to the given action
   * @deprecated use {@link CommonActionsPanel#getPreferredPopupPoint(AnAction)} instead
   */
  @Deprecated(forRemoval = true)
  public static @Nullable RelativePoint computePreferredPopupPoint(@NotNull JComponent toolbar, @NotNull AnAction action) {
    return CommonActionsPanel.computePreferredPopupPoint(toolbar, action);
  }

  public void addActionButtonListener(ActionButtonListener l, Disposable parentDisposable) {
    myListeners.add(l);
    Disposer.register(parentDisposable, () -> myListeners.remove(l));
  }

  public boolean removeActionButtonListener(ActionButtonListener l) {
    return myListeners.remove(l);
  }

  public static class CheckedAnActionButton extends AnActionButtonWrapper implements CheckedActionGroup {
    public CheckedAnActionButton(Presentation presentation, @NotNull AnAction action) {
      super(presentation, action);
    }
  }

  public static class AnActionButtonWrapper extends AnActionButton implements ActionWithDelegate<AnAction> {
    private final AnAction myAction;

    public AnActionButtonWrapper(Presentation presentation, @NotNull AnAction action) {
      super(presentation.getText(), presentation.getDescription(), presentation.getIcon());
      myAction = action;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myAction.actionPerformed(new AnActionEventWrapper(e, this));
    }

    @Override
    public void updateButton(@NotNull AnActionEvent e) {
      myAction.update(e);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return myAction.getActionUpdateThread();
    }

    @Override
    public boolean isDumbAware() {
      return myAction.isDumbAware();
    }

    @NotNull
    @Override
    public AnAction getDelegate() {
      return myAction;
    }
  }

  public static class ToggleableButtonWrapper extends AnActionButtonWrapper implements Toggleable {
    public ToggleableButtonWrapper(Presentation presentation, @NotNull AnAction action) {
      super(presentation, action);
    }
  }

  @SuppressWarnings("ComponentNotRegistered")
  public static class GroupPopupWrapper extends AnActionButtonWrapper {
    public GroupPopupWrapper(@NotNull ActionGroup group) {
      super(group.getTemplatePresentation(), group);
      setShortcut(group.getShortcutSet());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      RelativePoint relativePoint = getPreferredPopupPoint();
      JBPopupMenu.showAt(relativePoint, ActionManager.getInstance().createActionPopupMenu(
        e.getPlace(), (ActionGroup)getDelegate()).getComponent());
    }
  }

  public static final class AnActionEventWrapper extends AnActionEvent {
    private final AnActionButton myPeer;

    private AnActionEventWrapper(AnActionEvent e, AnActionButton peer) {
      super(e.getInputEvent(), e.getDataContext(), e.getPlace(), e.getPresentation(), e.getActionManager(), e.getModifiers());
      myPeer = peer;
    }

    public void showPopup(JBPopup popup) {
      popup.show(myPeer.getPreferredPopupPoint());
    }
  }

  public interface ActionButtonListener {
    default void isVisibleChanged(boolean newValue) {
      // Nothing
    }

    default void isEnabledChanged(boolean newValue) {
      // Nothing
    }
  }
}
