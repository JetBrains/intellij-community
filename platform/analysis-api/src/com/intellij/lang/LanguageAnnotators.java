// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.lang;

import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.KeyedLazyInstance;
import org.jetbrains.annotations.NonNls;

public final class LanguageAnnotators extends LanguageExtension<Annotator> {
  @NonNls public static final ExtensionPointName<KeyedLazyInstance<Annotator>> EP_NAME = ExtensionPointName.create("com.intellij.annotator");

  public static final LanguageAnnotators INSTANCE = new LanguageAnnotators();

  private LanguageAnnotators() {
    super(EP_NAME);
  }
}
