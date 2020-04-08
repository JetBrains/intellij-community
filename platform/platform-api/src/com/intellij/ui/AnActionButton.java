// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.containers.SmartHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * @author Konstantin Bulenkov
 */
public abstract class AnActionButton extends AnAction implements ShortcutProvider {
  private boolean myEnabled = true;
  private boolean myVisible = true;
  private ShortcutSet myShortcut;
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
    final AnActionButtonWrapper button = action instanceof CheckedActionGroup ? new CheckedAnActionButton(presentation, action)
                                                                              : new AnActionButtonWrapper(presentation, action);
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
    boolean enabled = isEnabled() && isContextComponentOk();
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
      myUpdaters = new SmartHashSet<>();
    }
    myUpdaters.add(updater);
  }

  public void updateButton(@NotNull AnActionEvent e) {
    final JComponent component = getContextComponent();
    e.getPresentation().setEnabled(component != null && component.isShowing() && component.isEnabled());
  }

  @Override
  public ShortcutSet getShortcut() {
    return myShortcut;
  }

  public void setShortcut(ShortcutSet shortcut) {
    myShortcut = shortcut;
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

  private boolean isContextComponentOk() {
    return myContextComponent == null
           || (myContextComponent.isVisible() && ComponentUtil.getParentOfType((Class<? extends JLayeredPane>)JLayeredPane.class,
                                                                               (Component)myContextComponent) != null);
  }

  @Nullable
  public final RelativePoint getPreferredPopupPoint() {
    Container c = myContextComponent;
    ActionToolbar toolbar = null;
    while ((c = c.getParent()) != null) {
      if (c instanceof JComponent
          && (toolbar = (ActionToolbar)((JComponent)c).getClientProperty(ActionToolbar.ACTION_TOOLBAR_PROPERTY_KEY)) != null) {
        break;
      }
    }
    if (toolbar instanceof JComponent) {
      for (Component comp : ((JComponent)toolbar).getComponents()) {
        if (comp instanceof ActionButtonComponent) {
          if (comp instanceof AnActionHolder) {
            if (((AnActionHolder)comp).getAction() == this) {
              return new RelativePoint(comp.getParent(), new Point(comp.getX(), comp.getY() + comp.getHeight()));
            }
          }
        }
      }
    }
    return null;
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
      final boolean enabled = e.getPresentation().isEnabled();
      final boolean visible = e.getPresentation().isVisible();
      if (enabled && visible) {
        super.updateButton(e);
      }
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

  public static class AnActionEventWrapper extends AnActionEvent {
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
