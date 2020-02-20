// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.ui.PatternFilterEditor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.classFilter.ClassFilterEditor;

public class CallerFiltersField extends ClassFiltersField {
  public CallerFiltersField(Project project, Disposable parent) {
    super(project, parent);
  }

  @Override
  protected EditClassFiltersDialog createEditDialog(Project project) {
    EditClassFiltersDialog dialog = new EditClassFiltersDialog(project) {
      @Override
      protected ClassFilterEditor createClassFilterEditor(Project project) {
        return new PatternFilterEditor(project);
      }
    };
    dialog.setTitle(JavaDebuggerBundle.message("caller.filters.dialog.title"));
    return dialog;
  }
}
