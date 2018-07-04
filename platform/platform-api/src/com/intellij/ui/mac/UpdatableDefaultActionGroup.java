// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac;

import com.intellij.openapi.actionSystem.DefaultActionGroup;

public class UpdatableDefaultActionGroup extends DefaultActionGroup {
  public static final String PROP_CHILDREN = "children";

  public void fireUpdate() { firePropertyChange(PROP_CHILDREN, null, null); }
}
