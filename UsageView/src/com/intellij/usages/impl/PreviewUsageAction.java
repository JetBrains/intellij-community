package com.intellij.usages.impl;

import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.UsageViewSettings;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
* @author cdr
*/
class PreviewUsageAction extends RuleAction {
  private static final Icon PREVIEW_ICON = IconLoader.getIcon("/actions/preview.png");

  PreviewUsageAction(UsageViewImpl usageView) {
    super(usageView, UsageViewBundle.message("preview.usages.action.text"), PREVIEW_ICON);
  }

  protected boolean getOptionValue() {
    return UsageViewSettings.getInstance().IS_PREVIEW_USAGES;
  }

  protected void setOptionValue(final boolean value) {
    UsageViewSettings.getInstance().IS_PREVIEW_USAGES = value;
  }
}
