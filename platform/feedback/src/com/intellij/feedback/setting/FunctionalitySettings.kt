package com.intellij.feedback.setting

/**
 * Plugin functionality settings.
 *
 * - *enableNotification* is responsible for displaying notifications.
 * If true, notifications are shown, otherwise they are not shown.
 *
 * - *enableWidgetIconColor* is responsible for the color of the widget icon.
 * If true, the widget icon is sometimes colored, otherwise the icon is always monochrome.
 *
 * - *enableWidget* is responsible for displaying the widget.
 * If true, the widget will work and sometimes display, otherwise the widget will never be displayed.
 *
 * @see kotlinFeedbackPlugin.user.ActiveUserType.showFeedbackNotification
 * @see kotlinFeedbackPlugin.ui.widget.FeedbackWidget.getIcon
 */
object FunctionalitySettings {

  private const val DEFAULT_ENABLE_NOTIFICATION = false
  private const val DEFAULT_ENABLE_WIDGET_ICON_COLOR = false
  private const val DEFAULT_ENABLE_WIDGET = true
  private const val DEFAULT_REQUIRED_IDEA_EAP_VERSION = false //TODO:Set true - false value just for testing

  val enableNotification: Boolean = /*getEnabledNotification() ?:*/ DEFAULT_ENABLE_NOTIFICATION
  val enableWidgetIconColor: Boolean = /*getEnableWidgetIconColor() ?:*/ DEFAULT_ENABLE_WIDGET_ICON_COLOR
  val enableWidget: Boolean = /*getEnabledWidget() ?:*/ DEFAULT_ENABLE_WIDGET
  val requiredIdeaEapVersion: Boolean = /*getRequiresIdeaEapVersion() ?:*/ DEFAULT_REQUIRED_IDEA_EAP_VERSION
}