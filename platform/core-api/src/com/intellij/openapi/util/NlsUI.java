// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.Nls;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

public class NlsUI {
  /**
   * Swing components
   */
  @NlsContext(prefix = "label")
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Target(ElementType.TYPE_USE)
  public @interface Label {
  }

  @NlsContext(prefix = "link.label")
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Target(ElementType.TYPE_USE)
  public @interface LinkLabel {
  }

  @NlsContext(prefix = "checkbox")
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Target(ElementType.TYPE_USE)
  public @interface Checkbox {
  }

  @NlsContext(prefix = "checkbox.tooltip")
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Target(ElementType.TYPE_USE)
  public @interface CheckboxTooltip {
  }

  @NlsContext(prefix = "titled.separator")
  @Nls(capitalization = Nls.Capitalization.Title)
  @Target(ElementType.TYPE_USE)
  public @interface TitledSeparator {
  }

  /**
   * Buttons
   */
  @NlsContext(prefix = "button")
  @Nls(capitalization = Nls.Capitalization.Title)
  @Target(ElementType.TYPE_USE)
  public @interface Button {
  }

  @NlsContext(prefix = "button.tooltip")
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Target(ElementType.TYPE_USE)
  public @interface ButtonTooltip {
  }
}
