package com.intellij.ui.popup;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ide.util.gotoByName.ChooseByNameBase;
import com.intellij.codeInsight.lookup.LookupManager;

import java.awt.*;

/**
 * @author yole
 */
public class NotLookupOrSearchCondition implements Condition<Project> {
  public static NotLookupOrSearchCondition INSTANCE = new NotLookupOrSearchCondition();

  private NotLookupOrSearchCondition() {
  }

  public boolean value(final Project project) {
    final Component focusedComponent = WindowManagerEx.getInstanceEx().getFocusedComponent(project);
    boolean fromQuickSearch =  focusedComponent != null && focusedComponent.getParent() instanceof ChooseByNameBase.JPanelProvider;
    return !fromQuickSearch && LookupManager.getInstance(project).getActiveLookup() == null;
  }
}