/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide.ui.customization;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
* User: anna
*/
public class CustomisedActionGroup extends ActionGroup {
  private boolean myForceUpdate;
  private final ActionGroup myGroup;
  private AnAction[] myChildren;
  private final CustomActionsSchema mySchema;
  private final String myDefaultGroupName;

  public CustomisedActionGroup(String shortName,
                               boolean popup,
                               final ActionGroup group,
                               CustomActionsSchema schema,
                               String defaultGroupName) {
    super(shortName, popup);
    myGroup = group;
    mySchema = schema;
    myDefaultGroupName = defaultGroupName;
    myForceUpdate = true;
  }

  @NotNull
  public AnAction[] getChildren(@Nullable final AnActionEvent e) {
    if (myForceUpdate){
      myChildren = CustomizationUtil.getReordableChildren(myGroup, mySchema, myDefaultGroupName, e);
      myForceUpdate = false;
      return myChildren;
    } else {
      if (!(myGroup instanceof DefaultActionGroup) || myChildren == null){
        myChildren = CustomizationUtil.getReordableChildren(myGroup, mySchema, myDefaultGroupName, e);
      }
      return myChildren;
    }
  }

  public void update(AnActionEvent e) {
    myGroup.update(e);
  }

  @Override
  public boolean isDumbAware() {
    return myGroup.isDumbAware();
  }

  @Nullable
  public AnAction getFirstAction() {
    final AnAction[] children = getChildren(null);
    return children.length > 0 ? children[0] : null;
  }
}
