// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName;

import com.intellij.psi.PsiElement;
import com.intellij.util.Processor;
import com.intellij.util.indexing.FindSymbolParameters;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ChooseByNameModelEx extends ChooseByNameModel {

  default void processNames(@NotNull Processor<? super String> processor, @NotNull FindSymbolParameters parameters) {
  }

  default @NotNull ChooseByNameItemProvider getItemProvider(@Nullable PsiElement context) {
    return new DefaultChooseByNameItemProvider(context);
  }

  static @NotNull ChooseByNameItemProvider getItemProvider(@NotNull ChooseByNameModel model, @Nullable PsiElement context) {
    return model instanceof ChooseByNameModelEx ? ((ChooseByNameModelEx)model).getItemProvider(context)
                                                : new DefaultChooseByNameItemProvider(context);
  }
}
