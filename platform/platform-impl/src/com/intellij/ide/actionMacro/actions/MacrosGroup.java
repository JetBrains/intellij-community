/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ide.actionMacro.actions;

import com.intellij.ide.actionMacro.ActionMacro;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import org.jetbrains.annotations.NotNull;
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
  @NotNull
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    ArrayList<AnAction> actions = new ArrayList<>();
    final ActionManagerEx actionManager = ((ActionManagerEx) ActionManager.getInstance());
    String[] ids = actionManager.getActionIds(ActionMacro.MACRO_ACTION_PREFIX);

    for (String id : ids) {
      actions.add(actionManager.getAction(id));
    }

    return actions.toArray(new AnAction[actions.size()]);
  }


}
