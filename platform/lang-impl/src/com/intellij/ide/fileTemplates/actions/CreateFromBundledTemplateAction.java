// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.fileTemplates.actions;

import com.intellij.ide.fileTemplates.FileTemplate;
import org.jetbrains.annotations.NotNull;

public interface CreateFromBundledTemplateAction {
  @NotNull FileTemplate getTemplate();
}
