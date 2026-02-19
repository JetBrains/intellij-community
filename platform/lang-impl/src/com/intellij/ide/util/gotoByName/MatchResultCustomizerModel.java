// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName;

import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.util.text.Matcher;
import com.intellij.util.text.matching.MatchingMode;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@ApiStatus.Internal
public interface MatchResultCustomizerModel {
  @NotNull List<@NotNull String> getAltNames(@NotNull String name);
}
