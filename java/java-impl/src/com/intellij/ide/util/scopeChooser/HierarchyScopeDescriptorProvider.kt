// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.scopeChooser

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager.Companion.getInstance

class HierarchyScopeDescriptorProvider : ScopeDescriptorProvider {
  override fun getScopeDescriptors(project: Project, dataContext: DataContext): Array<ScopeDescriptor> =
    if (Comparing.strEqual(getInstance(project).activeToolWindowId, ToolWindowId.TODO_VIEW)) {
      ScopeDescriptorProvider.EMPTY
    }
    else arrayOf(ClassHierarchyScopeDescriptor(project, dataContext))
}