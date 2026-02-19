// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.impl.elements;

import com.intellij.openapi.compiler.JavaCompilerBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public abstract class ModuleOutputElementTypeBase<E extends ModulePackagingElementBase> extends ModuleElementTypeBase<E> {
  public ModuleOutputElementTypeBase(String id, Supplier<@Nls(capitalization = Nls.Capitalization.Title) String> presentableName) {
    super(id, presentableName);
  }

  @Override
  public @NotNull String getElementText(@NotNull String moduleName) {
    return JavaCompilerBundle.message("node.text.0.compile.output", moduleName);
  }
}
