package com.intellij.openapi.options;

import java.util.List;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public abstract class CompositeConfigurable<T extends UnnamedConfigurable> extends BaseConfigurable {
  private List<T> myConfigurables;

  public void reset() {
    for (T configurable : getConfigurables()) {
      configurable.reset();
    }
  }

  public void apply() throws ConfigurationException {
    for (T configurable : getConfigurables()) {
      configurable.apply();
    }
  }

  public boolean isModified() {
    for (T configurable : getConfigurables()) {
      if (configurable.isModified()) {
        return true;
      }
    }
    return false;
  }

  public void disposeUIResources() {
    if (myConfigurables != null) {
      for (final T myConfigurable : myConfigurables) {
        myConfigurable.disposeUIResources();
      }
      myConfigurables = null;
    }
  }

  protected abstract List<T> createConfigurables();

  public List<T> getConfigurables() {
    if (myConfigurables == null) {
      myConfigurables = createConfigurables();
    }
    return myConfigurables;
  }
}
