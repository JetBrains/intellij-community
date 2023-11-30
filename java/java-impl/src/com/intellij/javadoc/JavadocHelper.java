/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.javadoc;

import com.intellij.application.options.CodeStyle;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

/**
 * This class is not singleton but provides {@link #getInstance() single-point-of-usage field}.
 */
public final class JavadocHelper extends AbstractBasicJavadocHelper {
  private static final JavadocHelper INSTANCE = new JavadocHelper();

  @Override
  protected boolean getJdAlignParamComments(@NotNull PsiFile psiFile) {
    final CodeStyleSettings codeStyleSettings = CodeStyle.getSettings(psiFile);
    return (codeStyleSettings.getCustomSettings(JavaCodeStyleSettings.class).JD_ALIGN_PARAM_COMMENTS);
  }

  public static @NotNull JavadocHelper getInstance() {
    return INSTANCE;
  }
}
