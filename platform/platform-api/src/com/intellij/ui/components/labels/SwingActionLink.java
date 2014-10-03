/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ui.components.labels;

import org.jetbrains.annotations.NotNull;

import javax.swing.Action;
import javax.swing.Icon;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeListener;

import static java.beans.EventHandler.create;

/**
 * @author Sergey.Malenkov
 */
public class SwingActionLink extends LinkLabel<Action> implements LinkListener<Action> {
  private final ActionEvent myEvent = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, Action.ACTION_COMMAND_KEY);

  public SwingActionLink(@NotNull Action action) {
    super((String)action.getValue(Action.NAME), (Icon)action.getValue(Action.SMALL_ICON));
    setToolTipText((String)action.getValue(Action.SHORT_DESCRIPTION));
    setVisible(action.isEnabled());
    setListener(this, action);
    action.addPropertyChangeListener(create(PropertyChangeListener.class, this, "visible", "source.enabled"));
  }

  @Override
  public void linkSelected(LinkLabel link, Action action) {
    action.actionPerformed(myEvent);
  }
}
