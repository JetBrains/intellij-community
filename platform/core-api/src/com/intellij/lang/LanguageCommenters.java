// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.lang;

public final class LanguageCommenters extends LanguageExtension<Commenter> {
  public static final LanguageCommenters INSTANCE = new LanguageCommenters();

  private LanguageCommenters() {
    super("com.intellij.lang.commenter");
  }

}