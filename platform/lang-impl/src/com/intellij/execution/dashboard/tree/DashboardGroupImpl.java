/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.execution.dashboard.tree;

import com.intellij.execution.dashboard.DashboardGroup;

import javax.swing.*;

/**
 * @author konstantin.aleev
 */
public class DashboardGroupImpl<T> implements DashboardGroup {
  private final T myValue;
  private final String myName;
  private final Icon myIcon;

  public DashboardGroupImpl(T value, String name, Icon icon) {
    myValue = value;
    myName = name;
    myIcon = icon;
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
    if (obj instanceof DashboardGroupImpl) {
      return myValue.equals(((DashboardGroupImpl)obj).myValue);
    }
    return false;
  }
}
