// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.help.impl;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dennis.Ushakov
 */
public class MacHelpUtil {
  static boolean invokeHelp(@NonNls @Nullable String id) {
    if (id == null || "top".equals(id)) id = "startpage";

    ID mainBundle = Foundation.invoke("NSBundle", "mainBundle");
    ID helpBundle = Foundation.invoke(mainBundle, "objectForInfoDictionaryKey:", Foundation.nsString("CFBundleHelpBookName"));
    if (helpBundle.equals(ID.NIL)) {
      return false;
    }

    ID helpManager = Foundation.invoke("NSHelpManager", "sharedHelpManager");
    Foundation.invoke(helpManager, "openHelpAnchor:inBook:", Foundation.nsString(id), helpBundle);
    return true;
  }

  static boolean isApplicable() {
    return SystemInfo.isMac && Registry.is("ide.mac.show.native.help") && !PlatformUtils.isCidr() && !PlatformUtils.isIdeaCommunity();
  }
}
