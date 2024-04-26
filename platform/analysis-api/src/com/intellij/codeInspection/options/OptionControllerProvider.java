// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import com.intellij.modcommand.ModCommand;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An extension point to provide context-specific options locatable with bindID string.
 * An implementor may use a prefix to handle all the options within that prefix.
 * The prefix is separated by dot '.' from the rest of the bindID. 
 * Please use prefixes mentioning your plugin name or related technology. 
 * Preferably the prefix name should be equal to the corresponding class name.
 * <p>
 *   All the registered providers form a tree, whose root is obtainable via
 *   {@link #rootController(PsiElement)}. All the children elements of the root
 *   are extensions registered in this extension point. One can create deeper hierarchy
 *   using static methods like 
 *   {@link OptionController#onPrefix(String, OptionController)} to delegate further prefixes
 *   to children controllers.
 * </p>
 * <p>
 *   When controller updates the option, it should fire any necessary events to
 *   let the corresponding listeners know about the changes. It's prohibited to
 *   require any user interaction or display any UI inside 
 *   {@link OptionController#getOption(String)} or
 *   {@link OptionController#setOption(String, Object)} implementations.
 *   It's assumed that {@code setOption} is executed in the write thread.
 * </p>
 * <p>
 *   The {@link OptionControllerProvider} can be used to set the option from within a
 *   {@link ModCommand} using methods like 
 *   {@link ModCommand#updateOption(PsiElement, String, Object)}. This allows to
 *   describe declaratively what are you going to set to set it later during 
 *   the action execution and properly process intention preview and undo/redo commands.
 * </p>
 * <h3>Example</h3>
 * <p>Imagine that you have a service class containing a writable field {@code data}:</p>
 * <pre>{@code
 * @Service
 * public final class MySettings {
 *   private String data;
 *
 *   public MySettings getInstance() {
 *     return ApplicationManager.getApplication().getService(MySettings.class);
 *   }
 * }}</pre>
 * <p>
 * If you want the field to be updatable from {@code OptionControllerProvider}, you may register your 
 * provider and provide an access to the {@code MySettings} fields:
 * </p>
 * <pre>{@code
 * public final class MySettingsProvider implements OptionControllerProvider {
 *   @Override
 *   public @NotNull OptionController forContext(@NotNull PsiElement context) {
 *     return OptionController.fieldsOf(getInstance());
 *   }
 *
 *   @Override
 *   public @NotNull String name() {
 *     return "MySettings";
 *   }
 * }}</pre>
 * <p>Now, bindID {@code "MySettings.data"} is available to read and write the field {@code data}. 
 * You can use it like this:
 * </p>
 * <pre>{@code
 *   OptionControllerProvider.rootController(context)
 *       .setOption("MySettings.data", "newValue");}</pre>
 * Also, you can generate a {@link ModCommand} that will do the same:
 * <pre>{@code
 *   ModCommand command = ModCommand.updateOption(context, "MySettings.data", "newValue");
 * }</pre>
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
