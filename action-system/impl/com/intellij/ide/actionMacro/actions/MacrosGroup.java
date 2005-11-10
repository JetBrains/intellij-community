package com.intellij.ide.actionMacro.actions;

import com.intellij.ide.actionMacro.ActionMacro;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jul 22, 2003
 * Time: 5:46:17 PM
 * To change this template use Options | File Templates.
 */
public class MacrosGroup extends ActionGroup {
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    ArrayList<AnAction> actions = new ArrayList<AnAction>();
    final ActionManagerEx actionManager = ((ActionManagerEx) ActionManager.getInstance());
    String[] ids = actionManager.getActionIds(ActionMacro.MACRO_ACTION_PREFIX);

    for (int i = 0; i < ids.length; i++) {
      String id = ids[i];
      actions.add(actionManager.getAction(id));
    }

    return actions.toArray(new AnAction[actions.size()]);
  }
}
