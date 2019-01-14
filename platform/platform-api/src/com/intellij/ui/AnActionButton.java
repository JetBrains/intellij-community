// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionButtonComponent;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.AnActionHolder;
import com.intellij.openapi.actionSystem.CheckedActionGroup;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ShortcutProvider;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.containers.SmartHashSet;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

  public AnActionButton(String text) {
    super(text);
  }

  public AnActionButton(String text, String description, @Nullable Icon icon) {
    super(text, description, icon);
  }

  public AnActionButton(String text, Icon icon) {
    this(text, null, icon);
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
           || (myContextComponent.isVisible() && UIUtil.getParentOfType(JLayeredPane.class, myContextComponent) != null);
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
    private final AnAction myDelegate;

    public CheckedAnActionButton(Presentation presentation, AnAction action) {
      super(presentation, action);
      myDelegate = action;
    }

    public AnAction getDelegate() {
      return myDelegate;
    }
  }

  public static class AnActionButtonWrapper extends AnActionButton {

    private final AnAction myAction;

    public AnActionButtonWrapper(Presentation presentation, AnAction action) {
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
