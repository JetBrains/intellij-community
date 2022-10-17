// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.settings;

import com.intellij.util.containers.ContainerUtil;

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

  @Override
  public void loadData(Object from) {
    myBindings.forEach(binding -> binding.loadData(from));
  }

  @Override
  public void saveData(Object to) {
    myBindings.forEach(binding -> binding.saveData(to));
  }

  @Override
  public boolean isModified(Object obj) {
    return ContainerUtil.exists(myBindings, binding -> binding.isModified(obj));
  }
}
