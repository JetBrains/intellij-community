// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.Nls;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

public class NlsProgress {
  @NlsContext(prefix = "progress.indicator.text.above")
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Target(ElementType.TYPE_USE)
  public @interface ProgressIndicatorTextAbove {
  }

  @NlsContext(prefix = "progress.indicator.text.below")
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Target(ElementType.TYPE_USE)
  public @interface ProgressIndicatorTextBelow {
  }

  @NlsContext(prefix = "progress.title")
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Target(ElementType.TYPE_USE)
  public @interface ProgressTitle {
  }

  /**
   * System notifications
   */

  @NlsContext(prefix = "system.notification.title")
  @Nls(capitalization = Nls.Capitalization.Title)
  @Target(ElementType.TYPE_USE)
  public @interface SystemNotificationTitle {
  }

  @NlsContext(prefix = "system.notification.text")
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Target(ElementType.TYPE_USE)
  public @interface SystemNotificationText {
  }
}