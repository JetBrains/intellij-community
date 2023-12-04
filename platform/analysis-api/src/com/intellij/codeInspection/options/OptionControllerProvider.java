// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An extension to provide context-specific options locatable with bindID string.
 * An implementor may use a prefix to handle all the options within that prefix.
 * Please use camelCase prefixes mentioning your plugin name or related technology.
 * <p>
 *   When controller updates the option, it should fire any necessary events to
 *   let the corresponding listeners know about the changes. It's prohibited to
 *   require any user interaction or display any UI inside 
 *   {@link OptionController#getOption(String)} or
 *   {@link OptionController#setOption(String, Object)} implementations.
 *   It's assumed that {@code setOption} is executed in the write thread.
 * </p>
 */
@ApiStatus.Experimental
public interface OptionControllerProvider {
  ExtensionPointName<OptionControllerProvider> EP_NAME = ExtensionPointName.create("com.intellij.optionController");

  /**
   * @param context context element (usually, a {@link PsiFile})
   * @return a controller that processes all the options within the prefix returned by {@link #name()} 
   * (followed by dot)
   */
  @NotNull OptionController forContext(@NotNull PsiElement context);

  /**
   * @return the name of this provider, which is used as a prefix. Must be a constant string.
   */
  @NotNull @NonNls String name();

  /**
   * @param context context element
   * @return a controller used to resolve the options within the specified context
   */
  static @NotNull OptionController rootController(@NotNull PsiElement context) {
    return OptionController.empty().onPrefixes(prefix -> {
      OptionControllerProvider provider = EP_NAME.getByKey(prefix, OptionControllerProvider.class, OptionControllerProvider::name);
      return provider == null ? null : provider.forContext(context);
    });
  }

  /**
   * @param context context element
   * @param bindId an option locator string
   * @return the option value. Supported value types are int, long, double, String, enum, List&lt;String&gt;. 
   * May return null, though this is rather an exceptional case. Check the corresponding {@link OptionControllerProvider}
   * for the detailed documentation about specific bindId you are using.
   * @throws IllegalArgumentException if bindId is not found
   */
  static @Nullable Object getOption(@NotNull PsiElement context, @NotNull String bindId) {
    return rootController(context).getOption(bindId);
  }

  /**
   * Sets the option value.
   * 
   * @param context context element
   * @param bindId an option locator string
   * @param value a value to set. It's the responsibility of the caller to provide the compatible value type.
   *              You may use {@link #getOption(PsiElement, String)} to check the current value type.
   *              Check the corresponding {@link OptionControllerProvider} for the detailed documentation 
   *              about specific bindId you are using.
   */
  static void setOption(@NotNull PsiElement context, @NotNull String bindId, @Nullable Object value) {
    rootController(context).setOption(bindId, value);
  }
}
