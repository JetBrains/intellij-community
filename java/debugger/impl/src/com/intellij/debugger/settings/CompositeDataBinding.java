/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.debugger.settings;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
public class CompositeDataBinding implements DataBinding{
  private final List<DataBinding> myBindings = new ArrayList<>();

  void addBinding(DataBinding binding) {
    myBindings.add(binding);
  }

  public void loadData(Object from) {
    myBindings.forEach(binding -> binding.loadData(from));
  }

  public void saveData(Object to) {
    myBindings.forEach(binding -> binding.saveData(to));
  }

  public boolean isModified(Object obj) {
    return myBindings.stream().anyMatch(binding -> binding.isModified(obj));
  }
}
