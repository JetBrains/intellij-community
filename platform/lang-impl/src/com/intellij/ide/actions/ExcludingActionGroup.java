/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.ArrayList;

/**
 * @author peter
 */
public class ExcludingActionGroup extends ActionGroup {
  private final ActionGroup myDelegate;
  private final Set<AnAction> myExcludes;

  public ExcludingActionGroup(ActionGroup delegate, Set<AnAction> excludes) {
    super(delegate.getTemplatePresentation().getText(), delegate.isPopup());
    myDelegate = delegate;
    myExcludes = excludes;
  }

  @Override
  public void update(AnActionEvent e) {
    myDelegate.update(e);
  }

  @NotNull
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    List<AnAction> result = new ArrayList<AnAction>();
    for (AnAction action : myDelegate.getChildren(e)) {
      if (myExcludes.contains(action)) {
        continue;
      }
      if (action instanceof ActionGroup) {
        result.add(new ExcludingActionGroup((ActionGroup) action, myExcludes));
      } else {
        result.add(action);
      }
    }
    return result.toArray(new AnAction[result.size()]);
  }
}
