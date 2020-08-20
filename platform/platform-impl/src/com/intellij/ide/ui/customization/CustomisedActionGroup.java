// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.customization;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.util.NlsActions;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class CustomisedActionGroup extends ActionGroup {
  private final ActionGroup myGroup;
  private AnAction[] myChildren;
  private final CustomActionsSchema mySchema;
  private final String myDefaultGroupName;
  private final String myRootGroupName;

  private int mySchemeModificationStamp = -1;
  private int myGroupModificationStamp = -1;

  public CustomisedActionGroup(@NlsActions.ActionText String shortName,
                               @NotNull ActionGroup group,
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

  @Override
  public AnAction @NotNull [] getChildren(@Nullable final AnActionEvent e) {
    int currentSchemaStamp = CustomActionsSchema.getInstance().getModificationStamp();
    int currentGroupStamp = myGroup instanceof DefaultActionGroup ? ((DefaultActionGroup)myGroup).getModificationStamp() : -1;
    if (mySchemeModificationStamp < currentSchemaStamp || myGroupModificationStamp < currentGroupStamp || ArrayUtil.isEmpty(myChildren) ||
        myGroup instanceof DynamicActionGroup || !(myGroup instanceof DefaultActionGroup)) {
      myChildren = CustomizationUtil.getReordableChildren(myGroup, mySchema, myDefaultGroupName, myRootGroupName, e);
      mySchemeModificationStamp = currentSchemaStamp;
      myGroupModificationStamp = currentGroupStamp;
    }
    return myChildren;
  }

  @Override
  public boolean isPopup() {
    return myGroup.isPopup();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    myGroup.update(e);
  }

  @Override
  public boolean isDumbAware() {
    return myGroup.isDumbAware();
  }

  @Override
  public boolean canBePerformed(@NotNull DataContext context) {
    return myGroup.canBePerformed(context);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    myGroup.actionPerformed(e);
  }

  @Nullable
  public AnAction getFirstAction() {
    final AnAction[] children = getChildren(null);
    return children.length > 0 ? children[0] : null;
  }

  public ActionGroup getOrigin() { return myGroup; }

  public void resetChildren() {
    myChildren = null;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof CustomisedActionGroup && Objects.equals(((CustomisedActionGroup)obj).getOrigin(), getOrigin());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getOrigin());
  }
}
