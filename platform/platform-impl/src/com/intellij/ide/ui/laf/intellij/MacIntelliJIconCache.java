// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.intellij;

import com.intellij.ide.ui.laf.IconCache;

import javax.swing.*;

/**
 * @Deprecated use {@link IconCache} instead
 */
@Deprecated
public class MacIntelliJIconCache {
  @Deprecated
  public static Icon getIcon(String name) {
    return IconCache.getIcon(name);
  }
}
