package com.intellij.openapi.actionSystem;

import com.intellij.icons.AllIcons;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Analogue of ToggleAction for option popups
 *
 * @author Konstantin Bulenkov
 * @since 12.1
 */
public abstract class CheckAction extends AnAction {
  public CheckAction(){
  }

  public CheckAction(@Nullable final String text){
    super(text);
  }

  public CheckAction(@Nullable final String text, @Nullable final String description){
    super(text, description, AllIcons.Actions.Checked);
  }

  public final void actionPerformed(final AnActionEvent e){
    final boolean state = !isSelected(e);
    setSelected(e, state);
  }

  public abstract boolean isSelected(AnActionEvent e);

  public abstract void setSelected(AnActionEvent e, boolean state);

  public void update(final AnActionEvent e){
    Icon icon = isSelected(e) ? AllIcons.Actions.Checked : EmptyIcon.create(AllIcons.Actions.Checked.getIconWidth());
    e.getPresentation().setIcon(icon);
  }
}
