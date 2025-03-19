// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options;

import com.intellij.openapi.util.text.Strings;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public interface Scheme {
  String EDITABLE_COPY_PREFIX = "_@user_";

  /**
   * @return An internal non-localizable name. The name is serialized when a scheme is saved and may be used as a scheme reference (ID).
   */
  @NotNull String getName();

  /**
   * @return A name to be shown in the UI; defaults to base name. Specific implementations may contain localization logic.
   */
  default @NotNull @Nls String getDisplayName() {
    return getBaseName(getName()); //NON-NLS
  }

  /**
   * Trims {@link #EDITABLE_COPY_PREFIX} from the given name.
   */
  static @NotNull String getBaseName(@NotNull String schemeName) {
    return Strings.trimStart(schemeName, EDITABLE_COPY_PREFIX);
  }
}
