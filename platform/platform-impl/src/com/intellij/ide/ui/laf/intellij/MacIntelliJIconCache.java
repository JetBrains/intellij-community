// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.intellij;

import com.intellij.util.ui.LafIconLookup;

import javax.swing.*;

/**
 * @Deprecated use {@link LafIconLookup} instead
 */
@Deprecated
public class MacIntelliJIconCache {
  @Deprecated
  public static Icon getIcon(String name) {
    return LafIconLookup.getIcon(name);
  }
}
