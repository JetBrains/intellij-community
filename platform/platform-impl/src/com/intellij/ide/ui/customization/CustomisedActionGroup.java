// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.customization;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.util.NlsActions;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class CustomisedActionGroup extends ActionGroupWrapper {
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
    super((group));
    getTemplatePresentation().setText(shortName);

    mySchema = schema;
    myDefaultGroupName = defaultGroupName;
    myRootGroupName = name;
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    ActionGroup delegate = getDelegate();
    int currentSchemaStamp = CustomActionsSchema.getInstance().getModificationStamp();
    int currentGroupStamp = !ActionUpdaterInterceptor.Companion.treatDefaultActionGroupAsDynamic() &&
                            delegate instanceof DefaultActionGroup group ? group.getModificationStamp() : -1;
    if (mySchemeModificationStamp < currentSchemaStamp ||
        myGroupModificationStamp < currentGroupStamp ||
        currentGroupStamp < 0 ||
        ArrayUtil.isEmpty(myChildren) ||
        delegate instanceof DynamicActionGroup) {
      AnAction[] originalChildren = super.getChildren(e);
      myChildren = CustomizationUtil.getReordableChildren(
        delegate, originalChildren, mySchema, myDefaultGroupName, myRootGroupName);
      mySchemeModificationStamp = currentSchemaStamp;
      myGroupModificationStamp = currentGroupStamp;
    }
    return myChildren;
  }

  @ApiStatus.Internal
  public AnAction @NotNull [] getDefaultChildrenOrStubs() {
    ActionGroup delegate = getDelegate();
    if (!(delegate instanceof DefaultActionGroup g)) return EMPTY_ARRAY;
    return CustomizationUtil.getReordableChildren(
      delegate, g.getChildActionsOrStubs(), mySchema, myDefaultGroupName, myRootGroupName);
  }

  public @Nullable AnAction getFirstAction() {
    AnAction[] children = getChildren(null);
    return children.length > 0 ? children[0] : null;
  }

  /** @deprecated Use {@link #getDelegate()} instead */
  @Deprecated(forRemoval = true)
  public @NotNull ActionGroup getOrigin() { return getDelegate(); }

  public void resetChildren() {
    myChildren = null;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof CustomisedActionGroup && Objects.equals(((CustomisedActionGroup)obj).getDelegate(), getDelegate());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getDelegate());
  }
}
