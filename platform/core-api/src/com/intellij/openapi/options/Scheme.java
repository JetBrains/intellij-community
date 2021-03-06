// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public interface Scheme {
  String EDITABLE_COPY_PREFIX = "_@user_";

  /**
   * @return An internal non-localizable name. The name is serialized when a scheme is saved and may be used as
   *         a scheme reference (ID).
   */
  @NotNull
  @NonNls
  String getName();

  /**
   * @return A name to be shown in UI, defaults to base name. Specific implementations may contain localization logic.
   */
  @NotNull
  @Nls
  default String getDisplayName() {
    return getBaseName(getName()); //NON-NLS
  }

  /**
   * @param schemeName The current scheme name.
   * @return A name without user prefix as in original scheme.
   */
  @NotNull
  @NonNls
  static String getBaseName(@NotNull @NonNls String schemeName) {
    return StringUtil.trimStart(schemeName, EDITABLE_COPY_PREFIX);
  }
}
