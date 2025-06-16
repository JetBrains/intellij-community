// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.impl.source.tree.JavaDocElementTypeFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Use the new Java syntax library instead.
 *             See {@link com.intellij.java.syntax.parser.JavaParser}
 */
@ApiStatus.ScheduledForRemoval
@Deprecated
public final class JavadocParser {

  private JavadocParser() { }

  public static void parseJavadocReference(@NotNull PsiBuilder builder) {
    BasicJavaDocParser.parseJavadocReference(builder, JavaParser.INSTANCE);
  }

  public static void parseJavadocType(@NotNull PsiBuilder builder) {
    BasicJavaDocParser.parseJavadocType(builder, JavaParser.INSTANCE);
  }

  public static void parseDocCommentText(@NotNull PsiBuilder builder) {
    BasicJavaDocParser.parseDocCommentText(builder, JavaDocElementTypeFactory.INSTANCE.getContainer());
  }
}