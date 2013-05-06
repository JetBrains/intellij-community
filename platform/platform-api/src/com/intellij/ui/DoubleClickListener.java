/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * @author max
 */
package com.intellij.ui;

import javax.swing.*;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.TreePath;
import java.awt.event.MouseEvent;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public abstract class DoubleClickListener extends ClickListener {
  @Override
  public final boolean onClick(MouseEvent event, int clickCount) {
    if (clickCount == 2 && event.getButton() == MouseEvent.BUTTON1) {
      return isExpandIconClick(event) ? false : onDoubleClick(event);
    }
    return false;
  }

  private static boolean isExpandIconClick(MouseEvent event) {
    if (event.getComponent() instanceof JTree) {
      JTree tree = (JTree)event.getComponent();
      TreePath treePath = tree.getClosestPathForLocation(event.getX(), event.getY());
      if (treePath == null || !(tree.getUI() instanceof BasicTreeUI)) return false;
      BasicTreeUI ui = (BasicTreeUI)tree.getUI();
      try {
        Method method = BasicTreeUI.class.getDeclaredMethod("isLocationInExpandControl", TreePath.class, Integer.TYPE, Integer.TYPE);
        method.setAccessible(true);
        Object result = method.invoke(ui, treePath, event.getX(), event.getY());
        if (result instanceof Boolean && result.equals(Boolean.TRUE)) return true;
      }
      catch (NoSuchMethodException e) {
      }
      catch (InvocationTargetException e) {
      }
      catch (IllegalAccessException e) {
      }
    }
    return false;
  }

  protected abstract boolean onDoubleClick(MouseEvent event);
}
