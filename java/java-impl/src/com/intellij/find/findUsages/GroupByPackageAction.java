// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.findUsages;

import com.intellij.icons.AllIcons;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.usages.impl.actions.RuleAction;

public class GroupByPackageAction extends RuleAction {
  public GroupByPackageAction() {
    super(JavaBundle.message("action.group.by.package"), AllIcons.Actions.GroupByPackage);
  }

  @Override
  protected boolean getOptionValue(AnActionEvent e) {
    return getUsageViewSettings(e).isGroupByPackage();
  }

  @Override
  protected void setOptionValue(AnActionEvent e, boolean value) {
    getUsageViewSettings(e).setGroupByPackage(value);
    if (value) {
      getUsageViewSettings(e).setGroupByDirectoryStructure(false); // mutually exclusive
    }
  }
}
