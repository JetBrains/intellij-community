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

  @NlsContext(prefix = "popup.advertisement")
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Target(ElementType.TYPE_USE)
  public @interface PopupAdvertisement {
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
  public @interface ConfigurableName {
  }

  @NlsContext(prefix = "parsing.error")
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Target(ElementType.TYPE_USE)
  public @interface ParsingError {
  }

  @NlsContext(prefix = "status.bar.text")
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Target(ElementType.TYPE_USE)
  public @interface StatusBarText {
  }

  /**
   * Use it for annotating OS provided notification title, such as "project built" or "tests running finished".
   * See also #SystemNotificationText.
   */
  @NlsContext(prefix = "system.notification.title")
  @Nls(capitalization = Nls.Capitalization.Title)
  @Target(ElementType.TYPE_USE)
  public @interface SystemNotificationTitle {
  }

  /**
   * Use it for annotating OS provided notification content.
   * See also #SystemNotificationTitle.
   */
  @NlsContext(prefix = "system.notification.text")
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Target(ElementType.TYPE_USE)
  public @interface SystemNotificationText {
  }

  @NlsContext(prefix = "command.name")
  @Nls(capitalization = Nls.Capitalization.Title)
  @Target(ElementType.TYPE_USE)
  public @interface Command {
  }

  @NlsContext(prefix = "tab.title")
  @Nls(capitalization = Nls.Capitalization.Title)
  @Target(ElementType.TYPE_USE)
  public @interface TabTitle {
  }

  /**
   * Annotate by {@code #AttributeDescriptor} text attribute keys, see {@link com.intellij.openapi.options.colors.AttributesDescriptor}
   */
  @NlsContext(prefix = "attribute.descriptor")
  @Nls(capitalization = Nls.Capitalization.Title)
  @Target(ElementType.TYPE_USE)
  public @interface AttributeDescriptor {
  }

  @NlsContext(prefix = "column.name")
  @Nls(capitalization = Nls.Capitalization.Title)
  @Target(ElementType.TYPE_USE)
  public @interface ColumnName {
  }

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

  @NlsContext(prefix = "radio")
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Target(ElementType.TYPE_USE)
  public @interface RadioButton {
  }

  @NlsContext(prefix = "border.title")
  @Nls(capitalization = Nls.Capitalization.Title)
  @Target(ElementType.TYPE_USE)
  public @interface BorderTitle {
  }

  @NlsContext(prefix = "tooltip")
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Target(ElementType.TYPE_USE)
  public @interface Tooltip {
  }

  @NlsContext(prefix = "separator")
  @Nls(capitalization = Nls.Capitalization.Title)
  @Target(ElementType.TYPE_USE)
  public @interface Separator {
  }

  @NlsContext(prefix = "button")
  @Nls(capitalization = Nls.Capitalization.Title)
  @Target(ElementType.TYPE_USE)
  public @interface Button {
  }

  @NlsContext(prefix = "text")
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Target(ElementType.TYPE_USE)
  public @interface DetailedDescription {
  }

  @NlsContext(prefix = "list.item")
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Target(ElementType.TYPE_USE)
  public @interface ListItem {
  }

  @NlsContext(prefix = "progress.text")
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Target(ElementType.TYPE_USE)
  public @interface ProgressText {
  }

  @NlsContext(prefix = "progress.details")
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Target(ElementType.TYPE_USE)
  public @interface ProgressDetails {
  }

  @NlsContext(prefix = "progress.title")
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Target(ElementType.TYPE_USE)
  public @interface ProgressTitle {
  }
}