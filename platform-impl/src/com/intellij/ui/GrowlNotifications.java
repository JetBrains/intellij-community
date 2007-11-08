package com.intellij.ui;

import com.apple.cocoa.application.NSApplication;
import com.apple.cocoa.foundation.NSArray;
import com.growl.Growl;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.TreeSet;

/**
 * @author mike
 */
class GrowlNotifications {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.GrowlNotifications");

  private static GrowlNotifications ourNotifications;
  private Growl myGrowl;
  private Set<String> myNotifications = new TreeSet<String>();

  public GrowlNotifications() {
    myGrowl = new Growl(
      ApplicationNamesInfo.getInstance().getFullProductName(),
      NSApplication.sharedApplication().applicationIconImage().TIFFRepresentation(),
        new NSArray(), new NSArray(), false);
    register();
  }

  private String[] getAllNotifications() {
    return myNotifications.toArray(new String[myNotifications.size()]);
  }

  public static synchronized GrowlNotifications getNofications() {
    if (ourNotifications == null) {
      ourNotifications = new GrowlNotifications();
    }

    return ourNotifications;
  }

  public void notify(@NotNull String notificationName, String title, String description) {
    if (!myNotifications.contains(notificationName)) {
      myNotifications.add(notificationName);
      register();
    }

    try {
      myGrowl.notifyGrowlOf(notificationName, title, description);
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  private void register() {
    myGrowl.setAllowedNotifications(getAllNotifications());
    try {
      myGrowl.setDefaultNotifications(getAllNotifications());
    }
    catch (Exception e) {
      LOG.error(e);
    }
    myGrowl.register();
  }
}
