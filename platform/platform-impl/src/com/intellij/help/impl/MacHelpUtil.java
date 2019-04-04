/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
