// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.services;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public interface ServiceViewFileEditorContributor<T> extends ServiceViewContributor<T> {
  T findService(@NotNull Project project, @NotNull FileEditor editor);
}