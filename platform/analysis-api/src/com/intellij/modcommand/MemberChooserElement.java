// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;

public interface MemberChooserElement {
  @NlsContexts.Label @NotNull String getText();
}
