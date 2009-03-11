package com.intellij.notification.impl;

import com.intellij.notification.NotificationDisplayType;
import org.jetbrains.annotations.NotNull;
import org.jdom.Element;

/**
 * @author spleaner
 */
public class NotificationSettings {

  private NotificationDisplayType myDisplayType;
  private boolean myCanDisable;
  private String myComponentName;
  private boolean myEnabled;

  public NotificationSettings(final String componentName, final NotificationDisplayType displayType,
                              final boolean canDisable) {
    this(componentName, displayType, true, canDisable);
  }

  public NotificationSettings(final String componentName, final NotificationDisplayType displayType, final boolean enabled,
                              final boolean canDisable) {
    myComponentName = componentName;
    myDisplayType = displayType;
    myEnabled = enabled;
    myCanDisable = canDisable;
  }

 @NotNull
  public String getComponentName() {
    return myComponentName;
  }

  @NotNull
  public NotificationDisplayType getDisplayType() {
    return myDisplayType;
  }

  public boolean isCanDisable() {
    return myCanDisable;
  }

  public static NotificationSettings load(@NotNull final Element element) {
    final String enabledValue = element.getAttributeValue("enabled");
    final boolean enabled = enabledValue == null || Boolean.valueOf(enabledValue).booleanValue();

    return new NotificationSettings(element.getAttributeValue("component"),
        NotificationDisplayType.valueOf(element.getAttributeValue("displayType")),
        enabled, Boolean.valueOf(element.getAttributeValue("canDisable")).booleanValue());
  }

  @NotNull
  public Element save() {
    final Element result = new Element("notification");

    result.setAttribute("component", getComponentName());
    result.setAttribute("displayType", getDisplayType().toString());
    result.setAttribute("canDisable", Boolean.valueOf(isCanDisable()).toString());
    result.setAttribute("enabled", Boolean.valueOf(isEnabled()).toString());

    return result;
  }

  public boolean isEnabled() {
    return myEnabled;
  }

  public void setEnabled(final boolean b) {
    myEnabled = b;
  }

  public void setDisplayType(final NotificationDisplayType displayType) {
    myDisplayType = displayType;
  }
}
