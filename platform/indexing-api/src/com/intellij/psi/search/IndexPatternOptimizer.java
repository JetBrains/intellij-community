// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Internal
public interface IndexPatternOptimizer {

 static IndexPatternOptimizer getInstance() {
   return ApplicationManager.getApplication().getService(IndexPatternOptimizer.class);
 }

 @NotNull List<String> extractStringsToFind(@NotNull String regexp);
}
