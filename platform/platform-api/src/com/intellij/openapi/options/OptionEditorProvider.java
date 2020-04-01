// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options;

import com.intellij.openapi.util.ClassExtension;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Implement this interface and register at {@code com.intellij.ui.optionEditorProvider} to provide UI for options.
 *
 * @param <O> type of options object
 */
@Experimental
public interface OptionEditorProvider<@NotNull O> {

  ClassExtension<OptionEditorProvider<?>> EXTENSION = new ClassExtension<>("com.intellij.ui.optionEditorProvider");

  @Contract("_ -> new")
  @SuppressWarnings("unchecked")
  static <@NotNull O> @NotNull OptionEditor<O> forOptions(O options) {
    OptionEditorProvider<?> provider = Objects.requireNonNull(
      EXTENSION.forClass(options.getClass()),
      "Option editor for class " + options.getClass().getName() + " must be registered"
    );
    return ((OptionEditorProvider<O>)provider).createOptionEditor(options);
  }

  /**
   * @return new instance of option editor initialized with {@code options}
   */
  @Contract("_ -> new")
  @NotNull OptionEditor<O> createOptionEditor(O options);
}
