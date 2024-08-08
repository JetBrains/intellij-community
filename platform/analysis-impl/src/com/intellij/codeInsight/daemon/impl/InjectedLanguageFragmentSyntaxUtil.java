// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.util.LayeredTextAttributes;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

class InjectedLanguageFragmentSyntaxUtil {
  @Contract(pure = true)
  static List<HighlightInfo> addSyntaxInjectedFragmentInfo(@NotNull EditorColorsScheme scheme,
                                                           @NotNull TextRange hostRange,
                                                           TextAttributesKey @NotNull [] keys, @Nullable Object toolId) {
    if (hostRange.isEmpty()) {
      return List.of();
    }
    // erase marker to override hosts colors
    HighlightInfo eraseInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.INJECTED_LANGUAGE_FRAGMENT)
      .range(hostRange)
      .textAttributes(TextAttributes.ERASE_MARKER)
      .createUnconditionally();
    eraseInfo.toolId = toolId;

    LayeredTextAttributes injectedAttributes = LayeredTextAttributes.create(scheme, keys);
    if (injectedAttributes.isEmpty() || keys.length == 1 && keys[0] == HighlighterColors.TEXT) {
      // nothing to add
      return List.of(eraseInfo);
    }

    HighlightInfo injectedInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.INJECTED_LANGUAGE_FRAGMENT)
      .range(hostRange)
      .textAttributes(injectedAttributes)
      .createUnconditionally();
    injectedInfo.toolId = toolId;
    return List.of(eraseInfo, injectedInfo);
  }
}
