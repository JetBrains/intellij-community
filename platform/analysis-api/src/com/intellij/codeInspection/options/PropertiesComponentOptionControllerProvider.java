// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.modcommand.ModCommand;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Provides the values of {@link PropertiesComponent} as options.
 * <p>
 * The bindId of an application-level value is {@code PropertiesComponent.application.<key>}, see {@link #applicationBindId(String)}.
 * The bindId of a project-level value is {@code PropertiesComponent.project.<key>}, see {@link #projectBindId(String)}.
 * <p>
 * The value of an option is the string that {@link PropertiesComponent} stores, or null if the key has no value.
 * The controller stores the string form of a new value, and a null value removes the key.
 */
@ApiStatus.Experimental
public final class PropertiesComponentOptionControllerProvider implements OptionControllerProvider {
  private static final @NonNls String NAME = "PropertiesComponent";
  private static final @NonNls String APPLICATION = "application";
  private static final @NonNls String PROJECT = "project";

  /**
   * @param key the key of the application-level {@link PropertiesComponent}
   * @return the bindId of the value of {@code key}
   */
  public static @NotNull @NonNls String applicationBindId(@NotNull @NonNls String key) {
    return NAME + "." + APPLICATION + "." + key;
  }

  /**
   * @param key the key of the project-level {@link PropertiesComponent}
   * @return the bindId of the value of {@code key}
   */
  public static @NotNull @NonNls String projectBindId(@NotNull @NonNls String key) {
    return NAME + "." + PROJECT + "." + key;
  }

  @Override
  public @NotNull OptionController forContext(@NotNull PsiElement context) {
    return OptionController.empty()
      .onPrefix(APPLICATION, controller(PropertiesComponent.getInstance()))
      .onPrefix(PROJECT, controller(PropertiesComponent.getInstance(context.getProject())));
  }

  private static @NotNull OptionController controller(@NotNull PropertiesComponent properties) {
    return OptionController.of(properties::getValue, (key, value) -> {
      if (value == null) {
        properties.unsetValue(key);
      }
      else {
        properties.setValue(key, value.toString());
      }
    });
  }

  @Override
  public @NotNull String name() {
    return NAME;
  }

  /**
   * Read a boolean option from {@link PropertiesComponent} via bindId
   *
   * @param context context element
   * @param bindId bindId to use (see {@link #applicationBindId(String)} and {@link #projectBindId(String)})
   * @return true if the option is stored and equals to true; false otherwise (absent option means false)
   */
  public static boolean getBoolean(@NotNull PsiElement context, @NotNull String bindId) {
    return OptionControllerProvider.getOption(context, bindId) instanceof String value && Boolean.parseBoolean(value);
  }

  /**
   * Generates a command to store a boolean option into {@link PropertiesComponent}.
   * Generates an empty command if the current value is already the desired one (assuming that absent value equals to false).
   *
   * @param context context element
   * @param bindId bindId to use (see {@link #applicationBindId(String)} and {@link #projectBindId(String)})
   * @param value value to set
   * @return a command to set the option.
   */
  public static @NotNull ModCommand setValue(@NotNull PsiElement context, @NotNull String bindId, boolean value) {
    if (getBoolean(context, bindId) == value) return ModCommand.nop();
    return ModCommand.updateOption(context, bindId, Boolean.toString(value));
  }
}
