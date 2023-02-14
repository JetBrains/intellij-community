// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.properties;

import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;

final class ForceBracesAccessor extends MagicIntegerConstAccessor {
  ForceBracesAccessor(@NotNull Object object, @NotNull Field field) {
    super(object,
          field,
          new int[]{
            CommonCodeStyleSettings.DO_NOT_FORCE,
            CommonCodeStyleSettings.FORCE_BRACES_IF_MULTILINE,
            CommonCodeStyleSettings.FORCE_BRACES_ALWAYS
          },
          new String[]{
            "never",
            "if_multiline",
            "always"
          });
  }
}
