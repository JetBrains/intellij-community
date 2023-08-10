// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;

/**
 * An element of member chooser
 */
public interface MemberChooserElement {
  /**
   * @return text to display for the element
   */
  @NlsContexts.Label @NotNull String getText();
}
