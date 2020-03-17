// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.lookup;

import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.InputEvent;

/**
 * @author peter
 */
public interface LookupEx extends Lookup {
  void setCurrentItem(LookupElement item);

  Component getComponent();

  void showElementActions(@Nullable InputEvent event);

  void hideLookup(boolean explicitly);
}
