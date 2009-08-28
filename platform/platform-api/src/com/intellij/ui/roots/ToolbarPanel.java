package com.intellij.ui.roots;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionToolbar;

import javax.swing.*;
import java.awt.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 22, 2004
 */
public class ToolbarPanel extends JPanel{
  public ToolbarPanel(JComponent contentComponent, ActionGroup actions) {
    super(new GridBagLayout());
    setBorder(BorderFactory.createEtchedBorder());
    if (contentComponent.getBorder() != null) {
      contentComponent.setBorder(BorderFactory.createEmptyBorder());
    }
    final ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, actions, true);
    add(actionToolbar.getComponent(), new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
    add(contentComponent, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.WEST, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
  }
}
