/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.actionSystem;

import javax.swing.*;

public abstract class ToggleAction extends AnAction {
  public static final String SELECTED_PROPERTY = "selected";

  public ToggleAction(){
  }

  public ToggleAction(final String text){
    super(text);
  }

  public ToggleAction(final String text, final String description, final Icon icon){
    super(text, description, icon);
  }

  public final void actionPerformed(final AnActionEvent e){
    final boolean state = !isSelected(e);
    setSelected(e, state);
    final Boolean selected = state ? Boolean.TRUE : Boolean.FALSE;
    final Presentation presentation = e.getPresentation();
    presentation.putClientProperty(SELECTED_PROPERTY, selected);
  }

  public abstract boolean isSelected(AnActionEvent e);

  public abstract void setSelected(AnActionEvent e, boolean state);

  public void update(final AnActionEvent e){
    final Boolean selected = isSelected(e) ? Boolean.TRUE : Boolean.FALSE;
    final Presentation presentation = e.getPresentation();
    presentation.putClientProperty(SELECTED_PROPERTY, selected);
  }
}
