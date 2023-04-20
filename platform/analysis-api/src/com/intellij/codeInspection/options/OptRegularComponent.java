// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import org.jetbrains.annotations.NotNull;

/**
 * A component that can be added as a child to most of other components, like {@link OptCheckbox}, {@link OptGroup}, etc.
 */
public sealed interface OptRegularComponent extends OptComponent
  permits OptCheckboxPanel, OptControl, OptCustom, OptGroup, OptHorizontalStack, OptSeparator, OptSettingLink, OptTabSet, OptTable {

  @Override
  @NotNull
  default OptRegularComponent prefix(@NotNull String bindPrefix) {
    return this;
  }
}
