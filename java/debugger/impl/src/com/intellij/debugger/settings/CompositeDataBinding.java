/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.settings;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * @author Eugene Zhuravlev
 * Date: Apr 12, 2005
 */
public class CompositeDataBinding implements DataBinding{
  private final List<DataBinding> myBindings = new ArrayList<DataBinding>();

  void addBinding(DataBinding binding) {
    myBindings.add(binding);
  }

  public void loadData(Object from) {
    for (Iterator<DataBinding> it = myBindings.iterator(); it.hasNext();) {
      it.next().loadData(from);
    }
  }

  public void saveData(Object to) {
    for (Iterator<DataBinding> it = myBindings.iterator(); it.hasNext();) {
      it.next().saveData(to);
    }
  }

  public boolean isModified(Object obj) {
    for (Iterator<DataBinding> it = myBindings.iterator(); it.hasNext();) {
      if (it.next().isModified(obj)) {
        return true;
      }
    }
    return false;
  }
}
