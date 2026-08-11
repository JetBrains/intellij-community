// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface JavaClassActionSuppressor {
  ExtensionPointName<JavaClassActionSuppressor> EP_NAME = ExtensionPointName.create("com.intellij.java.class.action.suppressor");

  boolean isSuppressed(@NotNull DataContext dataContext);
}
