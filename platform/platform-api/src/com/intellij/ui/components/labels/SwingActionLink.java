// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components.labels;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.beans.EventHandler;
import java.beans.PropertyChangeListener;

/**
 * @deprecated use {@link com.intellij.ui.components.ActionLink} instead
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
public class SwingActionLink extends LinkLabel<Action> implements LinkListener<Action> {
  private final ActionEvent myEvent = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, Action.ACTION_COMMAND_KEY);

  public SwingActionLink(@NotNull Action action) {
    super((String)action.getValue(Action.NAME), (Icon)action.getValue(Action.SMALL_ICON));
    setToolTipText((String)action.getValue(Action.SHORT_DESCRIPTION));
    setVisible(action.isEnabled());
    setListener(this, action);
    action.addPropertyChangeListener(EventHandler.create(PropertyChangeListener.class, this, "visible", "source.enabled"));
  }

  @Override
  public void linkSelected(LinkLabel<Action> link, Action action) {
    action.actionPerformed(myEvent);
  }
}
