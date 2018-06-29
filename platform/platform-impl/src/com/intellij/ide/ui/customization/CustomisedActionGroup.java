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

import com.intellij.openapi.actionSystem.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CustomisedActionGroup extends ActionGroup {
  private final ActionGroup myGroup;
  private AnAction[] myChildren;
  private final CustomActionsSchema mySchema;
  private final String myDefaultGroupName;
  private final String myRootGroupName;

  private int mySchemeModificationStamp = -1;

  public CustomisedActionGroup(String shortName,
                               final ActionGroup group,
                               CustomActionsSchema schema,
                               String defaultGroupName, 
                               String name) {
    copyFrom(group);
    getTemplatePresentation().setText(shortName);
    setPopup(group.isPopup());

    myGroup = group;
    mySchema = schema;
    myDefaultGroupName = defaultGroupName;
    myRootGroupName = name;
  }

  @NotNull
  public AnAction[] getChildren(@Nullable final AnActionEvent e) {
    int currentStamp = CustomActionsSchema.getInstance().getModificationStamp();
    if (mySchemeModificationStamp < currentStamp || myChildren == null || !(myGroup instanceof DefaultActionGroup)) {
      myChildren = CustomizationUtil.getReordableChildren(myGroup, mySchema, myDefaultGroupName, myRootGroupName, e);
      mySchemeModificationStamp = currentStamp;
    }
    return myChildren;
  }

  @Override
  public boolean isPopup() {
    return myGroup.isPopup();
  }

  public void update(AnActionEvent e) {
    myGroup.update(e);
  }

  @Override
  public boolean isDumbAware() {
    return myGroup.isDumbAware();
  }

  @Override
  public boolean canBePerformed(DataContext context) {
    return myGroup.canBePerformed(context);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    myGroup.actionPerformed(e);
  }

  @Nullable
  public AnAction getFirstAction() {
    final AnAction[] children = getChildren(null);
    return children.length > 0 ? children[0] : null;
  }

  public ActionGroup getOrigin() { return myGroup; }
}
