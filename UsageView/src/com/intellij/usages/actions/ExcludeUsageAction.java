package com.intellij.usages.actions;

import com.intellij.usages.Usage;
import com.intellij.usages.UsageView;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 22, 2004
 * Time: 9:04:56 PM
 * To change this template use File | Settings | File Templates.
 */
public class ExcludeUsageAction extends IncludeExcludeActionBase {
  protected void process(Usage[] usages, UsageView usageView) {
    usageView.excludeUsages(usages);
  }
}
