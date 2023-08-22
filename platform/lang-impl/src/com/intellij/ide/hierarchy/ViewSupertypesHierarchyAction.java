// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.hierarchy;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import org.jetbrains.annotations.Nls;

public final class ViewSupertypesHierarchyAction extends ChangeViewTypeActionBase {
  public ViewSupertypesHierarchyAction() {
    super(IdeBundle.messagePointer("action.view.supertypes.hierarchy"),
          IdeBundle.messagePointer("action.description.view.supertypes.hierarchy"), AllIcons.Hierarchy.Supertypes);
  }

  @Override
  protected @Nls String getTypeName() {
    return TypeHierarchyBrowserBase.getSupertypesHierarchyType();
  }
}
