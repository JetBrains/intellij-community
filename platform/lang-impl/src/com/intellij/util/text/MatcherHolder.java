// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public interface MatcherHolder {
  static void associateMatcher(@NotNull JComponent component, @Nullable Matcher matcher) {
    component.putClientProperty(MatcherHolder.class, matcher);
  }

  static @Nullable Matcher getAssociatedMatcher(@NotNull JComponent component) {
    return (Matcher)component.getClientProperty(MatcherHolder.class);
  }
}
