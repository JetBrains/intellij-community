package com.intellij.usages;

import com.intellij.openapi.Disposeable;

import javax.swing.*;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 16, 2004
 * Time: 4:13:28 PM
 * To change this template use File | Settings | File Templates.
 */
public interface UsageView extends Disposeable {
  /**
   * Returns {@link com.intellij.usages.UsageTarget} to look usages for
   */
  String USAGE_TARGETS = "usageTarget";
  /**
   * Returns {@link com.intellij.usages.Usage} which are selected in usage view
   */
  String USAGES = "usages";

  String USAGE_VIEW = "UsageView.new";

  void appendUsage(Usage usage);
  void includeUsages(Usage[] usages);
  void excludeUsages(Usage[] usages);

  void close();
  boolean isSearchInProgress();

  void addButtonToLowerPane(Runnable runnable, String text, char mnemonic);
  void addPerformOperationAction(Runnable processRunnable, String commandName, String cannotMakeString, String shortDescription, char mnemonic);
  UsageViewPresentation getPresentation();

  Set<Usage> getExcludedUsages();

  JComponent getComponent();
}
