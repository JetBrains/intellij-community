// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.config;

import org.jetbrains.annotations.NonNls;

public final class IntProperty extends ValueProperty<Integer> {
  public IntProperty(@NonNls String name, int defaultValue) {
    super(name, new Integer(defaultValue));
  }

  public int value(AbstractPropertyContainer container) {
    return get(container).intValue();
  }

  public void primSet(AbstractPropertyContainer container, int value) {
    set(container, new Integer(value));
  }
}
