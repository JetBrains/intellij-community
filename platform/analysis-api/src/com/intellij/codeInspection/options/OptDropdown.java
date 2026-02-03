// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @param bindId identifier of binding variable used by inspection; the corresponding variable is expected to be a string, boolean, or enum
 * @param splitLabel label to display around the control
 * @param options drop-down options; must have no repeating keys
 */
public record OptDropdown(@Language("jvm-field-name") @NotNull String bindId, 
                          @NotNull LocMessage splitLabel, 
                          @NotNull List<@NotNull Option> options) implements OptControl, OptRegularComponent {
  
  public OptDropdown {
    Set<String> uniqueKeys = new HashSet<>();
    for (Option option : options) {
      if (!uniqueKeys.add(option.key)) {
        throw new IllegalArgumentException("Duplicating key: " + option.key);
      }
    }
  }

  /**
   * @param key key to look for
   * @return an option that corresponds to the specified key; null if not found
   */
  public @Nullable Option findOption(@NotNull Object key) {
    String keyToLookup = key instanceof Enum<?> e ? e.name() : key.toString();
    return ContainerUtil.find(options, opt -> opt.key().equals(keyToLookup));
  }

  @Override
  public @NotNull OptDropdown prefix(@NotNull String bindPrefix) {
    return new OptDropdown(bindPrefix + "." + bindId, splitLabel, options);
  }

  /**
   * Drop down option
   * 
   * @param key key to assign to a variable. Can be one of following:
   *            Enum constant name, if binding variable is enum;
   *            Boolean constant name ("true" or "false"), if binding variable is boolean;
   *            Unique string, if binding variable is string
   * @param label label for a given option
   */
  public record Option(@NotNull String key, @NotNull LocMessage label) {}
}
