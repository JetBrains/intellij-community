package com.intellij.diagnostic.logging;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Sep 11, 2009
 * Time: 2:34:25 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class IndependentLogFilter extends LogFilter {
  protected IndependentLogFilter(String name) {
    super(name);
  }

  public abstract void selectFilter();

  public abstract boolean isSelected();
}
