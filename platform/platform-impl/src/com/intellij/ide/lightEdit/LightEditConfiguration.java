// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit;

import java.util.HashMap;
import java.util.Map;

class LightEditConfiguration {
  public boolean             autosaveMode       = false;
  public Map<String, String> pathToExtensionMap = new HashMap<>();
}
