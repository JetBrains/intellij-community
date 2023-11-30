// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeStyle;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.util.LineSeparator;

/**
 * @author Nikolai Matveev
 */
public final class ConvertToMacLineSeparatorsAction extends AbstractConvertLineSeparatorsAction {
  public ConvertToMacLineSeparatorsAction() {
    super(ApplicationBundle.messagePointer("combobox.crlf.mac"), LineSeparator.CR);
  }
}
