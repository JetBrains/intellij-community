// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.impl.elements;

import com.intellij.openapi.compiler.JavaCompilerBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public abstract class ModuleOutputElementTypeBase<E extends ModulePackagingElementBase> extends ModuleElementTypeBase<E> {
  public ModuleOutputElementTypeBase(String id, Supplier<@Nls(capitalization = Nls.Capitalization.Title) String> presentableName) {
    super(id, presentableName);
  }

  @NotNull
  @Override
  public String getElementText(@NotNull String moduleName) {
    return JavaCompilerBundle.message("node.text.0.compile.output", moduleName);
  }
}
