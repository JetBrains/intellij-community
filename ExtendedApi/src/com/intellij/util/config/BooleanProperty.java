package com.intellij.util.config;

import org.jetbrains.annotations.NonNls;

public class BooleanProperty extends ValueProperty<Boolean> {
  public BooleanProperty(@NonNls String name, boolean defaultValue) {
    super(name, Boolean.valueOf(defaultValue));
  }

  public boolean value(AbstractPropertyContainer container) {
    return get(container).booleanValue();
  }

  public void primSet(AbstractPropertyContainer container, boolean value) {
    set(container, Boolean.valueOf(value));
  }
}
