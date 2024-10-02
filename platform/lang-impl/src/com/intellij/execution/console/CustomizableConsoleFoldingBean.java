// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.console;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.ApiStatus;

/**
 * Provide default settings for folding lines in console output.
 * <p>
 * Augments user configurable entries in "Editor | General | Console".
 * <p>
 * Register in {@code plugin.xml}:<p>
 * {@code <stacktrace.fold substring="at com.intellij.ide.IdeEventQueue"/>}
 */
@ApiStatus.Internal
public final class CustomizableConsoleFoldingBean {
  public static final ExtensionPointName<CustomizableConsoleFoldingBean> EP_NAME = new ExtensionPointName<>("com.intellij.stacktrace.fold");

  /**
   * Fold lines that contain this text.
   */
  @RequiredElement
  @Attribute("substring")
  public String substring;

  /**
   * If {@code true} suppresses folding.
   */
  @Attribute("negate")
  public boolean negate = false;
}
