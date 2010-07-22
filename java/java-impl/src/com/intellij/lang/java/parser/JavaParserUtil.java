/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.lang.java.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilderUtil;
import com.intellij.openapi.util.Key;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;


public class JavaParserUtil {
  private static final Key<LanguageLevel> LANG_LEVEL_KEY = Key.create("JavaParserUtil.LanguageLevel");

  private JavaParserUtil() { }

  public static void setLanguageLevel(final PsiBuilder builder, final LanguageLevel level) {
    builder.putUserData(LANG_LEVEL_KEY, level);
  }

  public static boolean areTypeAnnotationsSupported(final PsiBuilder builder) {
    final LanguageLevel level = getLanguageLevel(builder);
    return level.isAtLeast(LanguageLevel.JDK_1_7);
  }

  @NotNull
  private static LanguageLevel getLanguageLevel(final PsiBuilder builder) {
    final LanguageLevel level = builder.getUserData(LANG_LEVEL_KEY);
    assert level != null;
    return level;
  }

  // used instead of PsiBuilder.error() as it drops all but first subsequent error messages
  public static void error(final PsiBuilder builder, final String message) {
    builder.mark().error(message);
  }

  public static boolean expectOrError(final PsiBuilder builder, final IElementType expectedType, final String errorMessage) {
    if (!PsiBuilderUtil.expect(builder, expectedType)) {
      error(builder, errorMessage);
      return false;
    }
    return true;
  }

  public static void emptyElement(final PsiBuilder builder, final IElementType type) {
    builder.mark().done(type);
  }
}
