// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.splitApi.frontend.tree;

import com.intellij.execution.dashboard.RunDashboardGroup;

import javax.swing.Icon;

/**
 * @author konstantin.aleev
 */
public class RunDashboardGroupImpl<T> implements RunDashboardGroup {
  private final T myValue;
  private final String myName;
  private final Icon myIcon;

  public RunDashboardGroupImpl(T value, String name, Icon icon) {
    myValue = value;
    myName = name;
    myIcon = icon;
  }

  public T getValue() {
    return myValue;
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public Icon getIcon() {
    return myIcon;
  }

  @Override
  public int hashCode() {
    return myValue.hashCode();
  }

  @Override
  public final boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj instanceof RunDashboardGroupImpl) {
      return myValue.equals(((RunDashboardGroupImpl<?>)obj).myValue);
    }
    return false;
  }
}
