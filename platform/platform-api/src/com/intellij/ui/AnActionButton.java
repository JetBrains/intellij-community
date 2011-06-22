/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.actionSystem.ShortcutProvider;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public abstract class AnActionButton extends AnAction implements ShortcutProvider {
  private boolean myEnabled = true;
  private boolean myVisible = true;
  private Shortcut myShortcut;
  private JComponent myContextComponent;

  protected AnActionButton(String text) {
    super(text);
  }

  protected AnActionButton(String text, String description, @Nullable Icon icon) {
    super(text, description, icon);
  }

  protected AnActionButton() {
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
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(isEnabled() && isContextComponentOk());
    e.getPresentation().setVisible(isVisible());
  }

  @Override
  public Shortcut getShortcut() {
    return myShortcut;
  }

  public void setShortcut(Shortcut shortcut) {
    myShortcut = shortcut;
  }

  public void setContextComponent(JComponent contextComponent) {
    myContextComponent = contextComponent;
  }

  private boolean isContextComponentOk() {
    return myContextComponent == null
           || (myContextComponent.isVisible() && UIUtil.getParentOfType(JLayeredPane.class, myContextComponent) != null);
  }
}
