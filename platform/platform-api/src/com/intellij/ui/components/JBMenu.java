/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui.components;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleState;
import javax.accessibility.AccessibleStateSet;
import javax.swing.*;

public class JBMenu extends JMenu implements Accessible {
  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleJBMenu();
    }
    return accessibleContext;
  }

  protected class AccessibleJBMenu extends AccessibleJMenu {
    @Override
    public AccessibleStateSet getAccessibleStateSet() {
      AccessibleStateSet set = super.getAccessibleStateSet();
      // Due to a bug in JMenu, CHECKED is added if the menu is enabled. That
      // is incorrect -- checked should be added only in the case of a "checkbox"
      // style menu.
      set.remove(AccessibleState.CHECKED);
      return set;
    }
  }
}
