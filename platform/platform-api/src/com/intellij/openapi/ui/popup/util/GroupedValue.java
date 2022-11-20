// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.popup.util;

import com.intellij.openapi.util.NlsContexts;

public interface GroupedValue {
  @NlsContexts.Separator String getSeparatorText();
}
