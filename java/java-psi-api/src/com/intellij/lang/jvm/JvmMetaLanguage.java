// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm;

import com.intellij.lang.Language;
import com.intellij.lang.MetaLanguage;
import org.jetbrains.annotations.NotNull;

/**
 * @since 2018.2
 */
public class JvmMetaLanguage extends MetaLanguage {

  protected JvmMetaLanguage() {
    super("JVM");
  }

  @Override
  public boolean matchesLanguage(@NotNull Language language) {
    return language instanceof JvmLanguage;
  }
}
