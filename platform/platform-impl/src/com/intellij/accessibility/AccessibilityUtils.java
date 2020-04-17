// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.accessibility;

import com.intellij.ide.GeneralSettings;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.util.User32Ex;
import com.sun.jna.platform.win32.WinDef.BOOLByReference;
import com.sun.jna.platform.win32.WinDef.UINT;

public final class AccessibilityUtils {
  public static void enableScreenReaderSupportIfNecessary() {
    if (GeneralSettings.isSupportScreenReadersOverridden()) {
      return;
    }

    if (isScreenReaderDetected()) {
      AccessibilityUsageTrackerCollector.SCREEN_READER_DETECTED.log();
      int answer = Messages.showYesNoDialog(ApplicationBundle.message("confirmation.screen.reader.enable"),
                                            ApplicationBundle.message("title.screen.reader.support"),
                                            Messages.getQuestionIcon());
      if (answer == Messages.YES) {
        AccessibilityUsageTrackerCollector.SCREEN_READER_SUPPORT_ENABLED.log();
        System.setProperty(GeneralSettings.SCREEN_READERS_DETECTED_PROPERTY, "true");
      }
    }
  }

  public static boolean isScreenReaderDetected() {
    if (SystemInfoRt.isWindows) {
      return isWindowsScreenReaderEnabled();
    }
    else if (SystemInfoRt.isMac) {
      return isMacVoiceOverEnabled();
    }
    return false;
  }

  /*
   * get MacOS NSWorkspace.shared.isVoiceOverEnabled property
   * https://developer.apple.com/documentation/devicemanagement/accessibility
  */
  private static boolean isMacVoiceOverEnabled() {
    Foundation.NSAutoreleasePool pool = new Foundation.NSAutoreleasePool();
    ID universalAccess = null;
    try {
      universalAccess = Foundation.invoke(
        Foundation.invoke("NSUserDefaults", "alloc"),
        "initWithSuiteName:",
        Foundation.nsString("com.apple.universalaccess")
      );
      ID voiceOverEnabledKey = Foundation.invoke(universalAccess, "boolForKey:", Foundation.nsString("voiceOverOnOffKey"));
      return voiceOverEnabledKey.intValue() != 0;
    }
    finally {
      if (universalAccess != null) Foundation.cfRelease(universalAccess);
      pool.drain();
    }
  }

  /*
  * get Windows SPI_GETSCREENREADER system parameter
  * https://docs.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-systemparametersinfoa#SPI_GETSCREENREADER
  */
  private static boolean isWindowsScreenReaderEnabled() {
    //get Windows SPI_GETSCREENREADER system parameter
    //https://docs.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-systemparametersinfoa#SPI_GETSCREENREADER
    BOOLByReference isActive = new BOOLByReference();
    boolean retValue = User32Ex.INSTANCE.SystemParametersInfo(new UINT(0x0046), new UINT(0), isActive, new UINT(0));
    return retValue && isActive.getValue().booleanValue();
  }
}
