// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import org.jetbrains.annotations.NotNull;

/**
 * A component that can be added as a child to most of other components, like {@link OptCheckbox}, {@link OptGroup}, etc.
 */
public sealed interface OptRegularComponent extends OptComponent
  permits OptCheckbox, OptCheckboxPanel, OptCustom, OptDropdown, OptExpandableString, OptGroup, OptHorizontalStack, OptMultiSelector,
          OptNumber, OptSeparator, OptSettingLink, OptString, OptStringList, OptTabSet, OptTable {

  @Override
  default @NotNull OptRegularComponent prefix(@NotNull String bindPrefix) {
    return this;
  }
}
