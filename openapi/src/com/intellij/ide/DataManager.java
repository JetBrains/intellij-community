package com.intellij.ide;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;

import java.awt.*;

public abstract class DataManager {
  public static DataManager getInstance() {
    return ApplicationManager.getApplication().getComponent(DataManager.class);
  }

  /**
   * @return {@link DataContext} constructed by the current focused component
   */
  public abstract DataContext getDataContext();

  /**
   * @return {@link DataContext} constructed by the specified <code>component</code>
   */
  public abstract DataContext getDataContext(Component component);

  /**
   * @return {@link DataContext} constructed be the specified <code>component</code>
   * and the point specified by <code>x</code> and <code>y</code> coordinate inside the
   * component.
   *
   * @exception java.lang.IllegalArgumentException if <code>component</code> is <code>null</code>
   * @exception java.lang.IllegalArgumentException if point <code>(x, y)</code> is not inside
   * component's bounds
   */
  public abstract DataContext getDataContext(Component component, int x, int y);
}
