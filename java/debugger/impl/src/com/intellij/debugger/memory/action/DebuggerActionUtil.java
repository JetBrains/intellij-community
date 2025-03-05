// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.memory.action;

import com.intellij.debugger.memory.ui.JavaTypeInfo;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.xdebugger.memory.ui.ClassesTable;
import com.intellij.xdebugger.memory.ui.TypeInfo;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.Nullable;

public final class DebuggerActionUtil {
  static TypeInfo getSelectedTypeInfo(AnActionEvent e) {
    return e.getData(ClassesTable.SELECTED_CLASS_KEY);
  }

  public static @Nullable ReferenceType getSelectedClass(AnActionEvent e) {
    TypeInfo typeInfo = getSelectedTypeInfo(e);
    return extractReferenceType(typeInfo);
  }

  private static @Nullable ReferenceType extractReferenceType(@Nullable TypeInfo typeInfo) {
    return typeInfo != null ? ((JavaTypeInfo)typeInfo).getReferenceType() : null;
  }
}
