package com.intellij.dupLocator;

import com.intellij.dupLocator.util.PsiFragment;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: Mar 29, 2004
 * Time: 5:10:31 PM
 * To change this template use File | Settings | File Templates.
 */
public interface DupInfo {
  int getPatterns();
  int getPatternCost(int number);
  int getPatternDensity(int number);
  PsiFragment[] getFragmentOccurences(int pattern);
  UsageInfo[] getUsageOccurences(int pattern);
  int getFileCount(final int pattern);
  @Nullable
  String getTitle(int pattern);
  @Nullable
  String getComment(int pattern);

  int getHash(final int i);
}
