// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * AnActionButton reinvents the action update wheel and breaks MVC.
 * We are slowly migrating to regular {@link AnAction}.
 */
@ApiStatus.Obsolete
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

  /** @deprecated  Use {@link ToolbarDecorator#addExtraAction(AnAction)} directly */
  @Deprecated(forRemoval = true)
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

  public @NotNull DataContext getDataContext() {
    return DataManager.getInstance().getDataContext(getContextComponent());
  }

  /** Use {@link com.intellij.openapi.ui.popup.JBPopupFactory#guessBestPopupLocation(AnAction, AnActionEvent)} instead */
  @ApiStatus.Obsolete
  public final @NotNull RelativePoint getPreferredPopupPoint() {
    RelativePoint result = CommonActionsPanel.getPreferredPopupPoint(this, myContextComponent);
    if (result != null) {
      return result;
    }
    LOG.error("Can't find toolbar button");
    return RelativePoint.getCenterOf(myContextComponent);
  }

  public void addActionButtonListener(ActionButtonListener l, Disposable parentDisposable) {
    myListeners.add(l);
    Disposer.register(parentDisposable, () -> myListeners.remove(l));
  }

  public boolean removeActionButtonListener(ActionButtonListener l) {
    return myListeners.remove(l);
  }

  /** @deprecated Use {@link ToolbarDecorator#addExtraAction(AnAction)} directly */
  @Deprecated(forRemoval = true)
  public static class CheckedAnActionButton extends AnActionButtonWrapper implements CheckedActionGroup {
    public CheckedAnActionButton(Presentation presentation, @NotNull AnAction action) {
      super(presentation, action);
    }
  }

  /** @deprecated Use {@link ToolbarDecorator#addExtraAction(AnAction)} directly */
  @Deprecated(forRemoval = true)
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

    @Override
    public @NotNull AnAction getDelegate() {
      return myAction;
    }
  }

  /** @deprecated Use {@link ToolbarDecorator#addExtraAction(AnAction)} directly */
  @Deprecated(forRemoval = true)
  private static class ToggleableButtonWrapper extends AnActionButtonWrapper implements Toggleable {
    public ToggleableButtonWrapper(Presentation presentation, @NotNull AnAction action) {
      super(presentation, action);
    }
  }

  /** @deprecated See {@link AnActionButtonWrapper} and {@link #getPreferredPopupPoint}*/
  @Deprecated(forRemoval = true)
  private static final class AnActionEventWrapper extends AnActionEvent {
    private final AnActionButton myPeer;

    private AnActionEventWrapper(AnActionEvent e, AnActionButton peer) {
      super(e.getDataContext(), e.getPresentation(), e.getPlace(),
            e.getUiKind(), e.getInputEvent(), e.getModifiers(), e.getActionManager());
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
