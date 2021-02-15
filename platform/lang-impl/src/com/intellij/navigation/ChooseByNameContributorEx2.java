// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.navigation;

import com.intellij.util.Processor;
import com.intellij.util.indexing.FindSymbolParameters;
import org.jetbrains.annotations.NotNull;

public interface ChooseByNameContributorEx2 extends ChooseByNameContributorEx {

  void processNames(@NotNull Processor<? super String> processor,
                    @NotNull FindSymbolParameters parameters);
}
