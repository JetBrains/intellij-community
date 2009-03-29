package com.intellij.notification.impl;

import com.intellij.notification.impl.ui.NotificationsConfigurablePanel;
import com.intellij.openapi.options.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.Nls;

import javax.swing.*;

/**
 * @author spleaner
 */
public class NotificationsConfigurable implements Configurable, SearchableConfigurable, OptionalConfigurable {
  public static final String DISPLAY_NAME = "Notifications";

  private NotificationsConfigurablePanel myComponent;

  public static NotificationsConfigurable getNotificationsConfigurable() {
    return ShowSettingsUtil.getInstance().findApplicationConfigurable(NotificationsConfigurable.class);
  }

  public static void editSettings() {
    final NotificationsConfigurable configurable = getNotificationsConfigurable();
    ShowSettingsUtil.getInstance().editConfigurable((Project) null, configurable);
  }

  @Nls
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return "reference.settings.ide.settings.notifications";
  }

  public JComponent createComponent() {
    if (myComponent == null) {
      myComponent = new NotificationsConfigurablePanel();
    }

    return myComponent;
  }

  public boolean isModified() {
    return myComponent != null && myComponent.isModified();
  }

  public void apply() throws ConfigurationException {
    myComponent.apply();
  }

  public void reset() {
    myComponent.reset();
  }

  public void disposeUIResources() {
    Disposer.dispose(myComponent);
    myComponent = null;
  }

  public String getId() {
    return getHelpTopic();
  }

  public Runnable enableSearch(final String option) {
    return null;
  }

  public boolean needDisplay() {
    return NotificationsConfiguration.getAllSettings().length > 0;
  }
}
