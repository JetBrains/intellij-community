// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.navigation;

import com.intellij.util.Processor;
import com.intellij.util.indexing.FindSymbolParameters;
import org.jetbrains.annotations.NotNull;

/**
 * Allows a plugin to add items to "Navigate Class|File|Symbol" lists.
 *
 * This interface allows plugins to implement contributor for the "class|file|symbol" lists which takes
 * names from external source.
 *
 * Difference between {@link ChooseByNameContributorEx} and {@link ChooseByNameContributorEx2}
 * is that the latter has access to the pattern at the processing names stage. This allows to use external
 * contributors which cannot provide all names, but rather expect to receive pattern and filter results
 * by themselves.
 */
public interface ChooseByNameContributorEx2 extends ChooseByNameContributorEx {

  void processNames(@NotNull Processor<? super String> processor,
                    @NotNull FindSymbolParameters parameters);
}
