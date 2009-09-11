package com.intellij.diagnostic.logging;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Sep 11, 2009
 * Time: 2:34:25 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class StandartLogFilter extends LogFilter {
  protected StandartLogFilter(String name) {
    super(name);
  }

  public abstract void selectFilter(LogConsolePreferences preferences);

  public abstract boolean isSelected(LogConsolePreferences preferences);
}
