/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.containers.SmartHashSet;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Set;

/**
 * @author Konstantin Bulenkov
 */
public abstract class AnActionButton extends AnAction implements ShortcutProvider {
  private boolean myEnabled = true;
  private boolean myVisible = true;
  private ShortcutSet myShortcut;
  private AnAction myAction = null;
  private JComponent myContextComponent;
  private Set<AnActionButtonUpdater> myUpdaters;

  public AnActionButton(String text) {
    super(text);
  }

  public AnActionButton(String text, String description, @Nullable Icon icon) {
    super(text, description, icon);
  }

  @SuppressWarnings("NullableProblems")
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
    myEnabled = enabled;
  }

  public boolean isVisible() {
    return myVisible;
  }

  public void setVisible(boolean visible) {
    myVisible = visible;
  }

  @Override
  public final void update(AnActionEvent e) {
    boolean myActionVisible = true;
    boolean myActionEnabled = true;
    if (myAction != null) {      
      myAction.update(e);
      myActionEnabled = e.getPresentation().isEnabled();
      myActionVisible = e.getPresentation().isVisible();
    }
    boolean enabled = isEnabled() && isContextComponentOk() && myActionEnabled;
    if (enabled && myUpdaters != null) {
      for (AnActionButtonUpdater updater : myUpdaters) {
        if (!updater.isEnabled(e)) {
          enabled = false;
          break;
        }
      }
    }
    e.getPresentation().setEnabled(enabled);
    e.getPresentation().setVisible(isVisible() && myActionVisible);

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

  public void updateButton(AnActionEvent e) {
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

  public DataContext getDataContext() {
    return DataManager.getInstance().getDataContext(getContextComponent());
  }

  private boolean isContextComponentOk() {
    return myContextComponent == null
           || (myContextComponent.isVisible() && UIUtil.getParentOfType(JLayeredPane.class, myContextComponent) != null);
  }

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

  private static class AnActionButtonWrapper extends AnActionButton {

    private final AnAction myAction;

    public AnActionButtonWrapper(Presentation presentation, AnAction action) {
      super(presentation.getText(), presentation.getDescription(), presentation.getIcon());
      myAction = action;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myAction.actionPerformed(new AnActionEventWrapper(e, this));
    }

    @Override
    public void updateButton(AnActionEvent e) {
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
}
