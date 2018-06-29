// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.action;

import com.intellij.xdebugger.memory.ui.ClassesTable;
import com.intellij.debugger.memory.ui.JavaTypeInfo;
import com.intellij.xdebugger.memory.ui.TypeInfo;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.Nullable;

public class ActionUtil {
  public static TypeInfo getSelectedTypeInfo(AnActionEvent e) {
    return e.getData(ClassesTable.SELECTED_CLASS_KEY);
  }

  @Nullable
  public static ReferenceType getSelectedClass(AnActionEvent e) {
    TypeInfo typeInfo = getSelectedTypeInfo(e);
    return extractReferenceType(typeInfo);
  }

  @Nullable
  private static ReferenceType extractReferenceType(@Nullable TypeInfo typeInfo) {
    return typeInfo != null ? ((JavaTypeInfo) typeInfo).getReferenceType() : null;
  }
}
