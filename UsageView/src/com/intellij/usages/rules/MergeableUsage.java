package com.intellij.usages.rules;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 28, 2004
 * Time: 12:06:13 PM
 * To change this template use File | Settings | File Templates.
 */
public interface MergeableUsage {
  /**
   * @param mergeableUsage
   * @return true if merge successed
   */
  boolean merge(MergeableUsage mergeableUsage);

  /**
   * Revert to original status prior any merges
   */
  void reset();
}
