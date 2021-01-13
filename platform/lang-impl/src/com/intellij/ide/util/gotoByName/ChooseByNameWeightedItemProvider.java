// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.gotoByName;

import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

public interface ChooseByNameWeightedItemProvider extends ChooseByNameItemProvider {

  boolean filterElementsWithWeights(@NotNull ChooseByNameViewModel base,
                                    @NotNull String pattern,
                                    boolean everywhere,
                                    @NotNull ProgressIndicator indicator,
                                    @NotNull Processor<? super FoundItemDescriptor<?>> consumer);
}
