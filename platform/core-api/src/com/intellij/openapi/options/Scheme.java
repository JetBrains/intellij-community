// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public interface Scheme {
  String EDITABLE_COPY_PREFIX = "_@user_";

  @NotNull
  @Nls(capitalization = Nls.Capitalization.Title)
  String getName();

  @NotNull
  @Nls
  default String getDisplayName() {
    return StringUtil.trimStart(getName(), EDITABLE_COPY_PREFIX);
  }
}
