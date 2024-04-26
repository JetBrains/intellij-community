// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.core;

import com.intellij.DynamicBundle;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiModifier;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

public final class JavaPsiBundle {
  public static final @NonNls String BUNDLE = "messages.JavaPsiBundle";
  public static final DynamicBundle INSTANCE = new DynamicBundle(JavaPsiBundle.class, BUNDLE);

  private JavaPsiBundle() { }

  public static @NotNull @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getMessage(key, params);
  }

  public static @NotNull Supplier<@Nls String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
    return INSTANCE.getLazyMessage(key, params);
  }

  public static boolean contains(String key) {
    return INSTANCE.containsKey(key);
  }

  /**
   * @param modifier modifier string constant
   * @return modifier to display to the user. 
   * Note that it's not localized in the usual sense: modifiers returned from this method are kept in English,
   * regardless of the active language pack.
   * It's believed that this way it's clearer.
   */
  public static @NotNull @NlsSafe String visibilityPresentation(@NotNull @PsiModifier.ModifierConstant String modifier) {
    return modifier.equals(PsiModifier.PACKAGE_LOCAL) ? "package-private" : modifier;
  }
}