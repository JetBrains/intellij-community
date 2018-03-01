// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.java;

import com.intellij.lang.IdeLanguageCustomization;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;

public class IntelliJIdeaLanguageCustomization extends IdeLanguageCustomization {
  @Override
  public Language getMainIdeLanguage() {
    return JavaLanguage.INSTANCE;
  }
}
