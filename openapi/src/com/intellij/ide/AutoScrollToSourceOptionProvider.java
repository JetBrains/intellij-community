package com.intellij.ide;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 20, 2004
 * Time: 10:55:14 PM
 * To change this template use File | Settings | File Templates.
 */
public interface AutoScrollToSourceOptionProvider {
  boolean isAutoScrollMode();
  void setAutoScrollMode(boolean state);
}
