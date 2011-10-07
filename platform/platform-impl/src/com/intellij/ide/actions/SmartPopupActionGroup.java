/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;

/**
 * @author yole
 */
public class SmartPopupActionGroup extends DefaultActionGroup {
  private boolean myIsPopupCalculated;

  @Override
  public boolean isPopup() {
    if (!myIsPopupCalculated) {
      setPopup(getChildrenCountRecursive(this) > 1);
      myIsPopupCalculated = true;
    }
    return super.isPopup();
  }

  private static int getChildrenCountRecursive(ActionGroup group) {
    AnAction[] children;
    if (group instanceof DefaultActionGroup) {
      children = ((DefaultActionGroup) group).getChildActionsOrStubs();
    }
    else {
      children = group.getChildren(null);
    }
    int count = 0;
    for (AnAction child : children) {
      if (child instanceof ActionGroup) {
        count += getChildrenCountRecursive((ActionGroup) child);
      }
      else {
        count++;
      }
    }
    return count;
  }
}
