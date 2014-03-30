/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.profile.codeInspection.ui;

import com.intellij.codeInspection.ex.Descriptor;
import com.intellij.codeInspection.ex.ScopeToolState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ClearableLazyValue;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.ui.CheckedTreeNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author anna
 * @since 14-May-2009
 */
public class InspectionConfigTreeNode extends CheckedTreeNode {
  private final ScopeToolState myState;
  private boolean myByDefault;
  private boolean myInspectionNode;
  private final ClearableLazyValue<Boolean> myProperSetting = new ClearableLazyValue<Boolean>() {
    @NotNull
    @Override
    protected Boolean compute() {
      Descriptor descriptor = getDescriptor();
      if (descriptor != null) return descriptor.getInspectionProfile().isProperSetting(descriptor.getToolWrapper().getShortName());
      for (int i = 0; i < getChildCount(); i++) {
        InspectionConfigTreeNode node = (InspectionConfigTreeNode)getChildAt(i);
        if (node.isProperSetting()) {
          return true;
        }
      }
      return false;
    }
  };

  public InspectionConfigTreeNode(@NotNull Object userObject, ScopeToolState state, boolean byDefault, boolean inspectionNode) {
    super(userObject);
    myState = state;
    myByDefault = byDefault;
    myInspectionNode = inspectionNode;
    if (state != null) {
      setChecked(state.isEnabled());
    }
  }

  public InspectionConfigTreeNode(@NotNull Descriptor descriptor, ScopeToolState state, boolean byDefault, boolean isEnabled,
                                  boolean inspectionNode) {
    this(descriptor, state, byDefault, inspectionNode);
    setChecked(isEnabled);
  }

  @Nullable
  public Descriptor getDescriptor() {
    if (userObject instanceof String) return null;
    return (Descriptor)userObject;
  }

  @Nullable
  public NamedScope getScope(Project project) {
    return myState == null ? null : myState.getScope(project);
  }

  public boolean isByDefault() {
    return myByDefault;
  }

  @Nullable
  public String getGroupName() {
    return userObject instanceof String ? (String)userObject : null;
  }

  public boolean isInspectionNode() {
    return myInspectionNode;
  }

  public void setInspectionNode(boolean inspectionNode) {
    myInspectionNode = inspectionNode;
  }

  public void setByDefault(boolean byDefault) {
    myByDefault = byDefault;
  }

  @Nullable
  public String getScopeName() {
    return myState != null ? myState.getScopeName() : null;
  }

  public boolean isProperSetting() {
    return myProperSetting.getValue();
  }

  public void dropCache() {
    myProperSetting.drop();
  }

  @Override
  public String toString() {
    if (userObject instanceof Descriptor) {
      return ((Descriptor)userObject).getText();
    }
    return super.toString();
  }
}