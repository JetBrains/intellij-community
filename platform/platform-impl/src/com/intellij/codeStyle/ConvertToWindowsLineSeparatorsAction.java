// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeStyle;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.util.LineSeparator;
import org.jetbrains.annotations.ApiStatus;

/**
 * @author Nikolai Matveev
 */
@ApiStatus.Internal
public final class ConvertToWindowsLineSeparatorsAction extends AbstractConvertLineSeparatorsAction {

  public ConvertToWindowsLineSeparatorsAction() {
    super(ApplicationBundle.messagePointer("combobox.crlf.windows"), LineSeparator.CRLF);
  }
}
