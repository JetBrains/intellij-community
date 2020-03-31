// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@ApiStatus.Experimental
public class NlsContexts {
  /**
   * Dialogs
   */
  @NlsContext(prefix = "dialog.title")
  @Nls(capitalization = Nls.Capitalization.Title)
  @Target(ElementType.TYPE_USE)
  public @interface DialogTitle {
  }

  @NlsContext(prefix = "dialog.message")
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Target(ElementType.TYPE_USE)
  public @interface DialogMessage {
  }

  @NlsContext(prefix = "input.dialog.initial.value")
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Target(ElementType.TYPE_USE)
  public @interface InputDialogInitialValue {
  }

  /**
   * Popups
   */
  @NlsContext(prefix = "popup.title")
  @Nls(capitalization = Nls.Capitalization.Title)
  @Target(ElementType.TYPE_USE)
  public @interface PopupTitle {
  }

  @NlsContext(prefix = "popup.content")
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Target(ElementType.TYPE_USE)
  public @interface PopupContent {
  }

  /**
   * Notifications
   */
  @NlsContext(prefix = "notification.title")
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Target(ElementType.TYPE_USE)
  public @interface NotificationTitle {
  }

  @NlsContext(prefix = "notification.subtitle")
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Target(ElementType.TYPE_USE)
  public @interface NotificationSubtitle {
  }

  @NlsContext(prefix = "notification.content")
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Target(ElementType.TYPE_USE)
  public @interface NotificationContent {
  }

  @NlsContext(prefix = "status.text")
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Target(ElementType.TYPE_USE)
  public @interface StatusText {
  }

  @NlsContext(prefix = "hint.text")
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Target(ElementType.TYPE_USE)
  public @interface HintText {
  }

  @NlsContext(prefix = "configurable.name")
  @Nls(capitalization = Nls.Capitalization.Title)
  @Target(ElementType.TYPE_USE)
  public @interface Configurable {
  }

  @NlsContext(prefix = "validation.info")
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Target(ElementType.TYPE_USE)
  public @interface ValidationInfo {
  }

  @NlsContext(prefix = "configuration.error.message")
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Target(ElementType.TYPE_USE)
  public @interface ConfigurationErrorMessage {
  }

  @NlsContext(prefix = "configuration.error.title")
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Target(ElementType.TYPE_USE)
  public @interface ConfigurationErrorTitle {
  }
}