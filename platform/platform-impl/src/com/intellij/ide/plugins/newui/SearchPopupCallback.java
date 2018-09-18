// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.util.Consumer;

/**
 * @author Alexander Lobas
 */
public abstract class SearchPopupCallback implements Consumer<String> {
  public String prefix;
}