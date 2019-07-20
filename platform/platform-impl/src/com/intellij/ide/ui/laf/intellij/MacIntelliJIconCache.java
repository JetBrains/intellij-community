// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.intellij;

import com.intellij.util.ui.LafIconLookup;

import javax.swing.*;

/**
 * @deprecated use {@link LafIconLookup} instead
 */
@Deprecated
public class MacIntelliJIconCache {

  /**
   * @deprecated use {@link LafIconLookup#getIcon(String)} instead
   */
  @Deprecated
  public static Icon getIcon(String name) {
    return LafIconLookup.getIcon(name);
  }
}
