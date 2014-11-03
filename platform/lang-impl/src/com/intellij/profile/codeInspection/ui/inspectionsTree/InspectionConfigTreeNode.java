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
package com.intellij.profile.codeInspection.ui.inspectionsTree;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.ex.Descriptor;
import com.intellij.openapi.util.ClearableLazyValue;
import com.intellij.profile.codeInspection.ui.ToolDescriptors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;

/**
 * @author anna
 * @since 14-May-2009
 */
public class InspectionConfigTreeNode extends DefaultMutableTreeNode {
  private final ClearableLazyValue<Boolean> myProperSetting = new ClearableLazyValue<Boolean>() {
    @NotNull
    @Override
    protected Boolean compute() {
      ToolDescriptors descriptors = getDescriptors();
      if (descriptors != null) {
        final Descriptor defaultDescriptor = descriptors.getDefaultDescriptor();
        return defaultDescriptor.getInspectionProfile().isProperSetting(defaultDescriptor.getToolWrapper().getShortName());
      }
      for (int i = 0; i < getChildCount(); i++) {
        InspectionConfigTreeNode node = (InspectionConfigTreeNode)getChildAt(i);
        if (node.isProperSetting()) {
          return true;
        }
      }
      return false;
    }
  };

  public InspectionConfigTreeNode(@NotNull Object userObject) {
    super(userObject);
  }

  public HighlightDisplayKey getKey() {
    return getDefaultDescriptor().getKey();
  }

  @Nullable
  public Descriptor getDefaultDescriptor() {
    final ToolDescriptors descriptors = getDescriptors();
    return descriptors == null ? null : descriptors.getDefaultDescriptor();
  }

  @Nullable
  public ToolDescriptors getDescriptors() {
    if (userObject instanceof String) return null;
    return (ToolDescriptors)userObject;
  }

  @Nullable
  public String getGroupName() {
    return userObject instanceof String ? (String)userObject : null;
  }

  @Nullable
  public String getScopeName() {
    final ToolDescriptors descriptors = getDescriptors();
    return descriptors != null ? descriptors.getDefaultScopeToolState().getScopeName() : null;
  }

  public boolean isProperSetting() {
    return myProperSetting.getValue();
  }

  public void dropCache() {
    myProperSetting.drop();
  }

  @Override
  public String toString() {
    if (userObject instanceof ToolDescriptors) {
      return ((ToolDescriptors)userObject).getDefaultDescriptor().getText();
    }
    if (userObject instanceof Descriptor) {
      return ((Descriptor)userObject).getText();
    }
    return super.toString();
  }
}