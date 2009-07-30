package com.intellij.debugger.impl;

/**
 * @author Eugene Zhuravlev
 *         Date: Jul 30, 2009
 */
public interface PrioritizedTask {
  enum Priority {
    HIGH, NORMAL, LOW
  }

  Priority getPriority();
}
