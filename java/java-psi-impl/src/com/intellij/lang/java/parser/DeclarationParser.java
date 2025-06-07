// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.parser;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated Use the new Java syntax library instead.
 *             See {@link com.intellij.java.syntax.parser.JavaParser}
 */
@Deprecated
public class DeclarationParser extends BasicDeclarationParser {
  public enum Context {
    FILE, CLASS, CODE_BLOCK, ANNOTATION_INTERFACE, JSHELL
  }

  public DeclarationParser(final @NotNull JavaParser javaParser) {
    super(javaParser);
  }

  //for backward compatibility
  @Override
  public @Nullable PsiBuilder.Marker parse(@NotNull PsiBuilder builder, BaseContext context) {
    return parse(builder, toContext(context));
  }

  private static Context toContext(BaseContext context) {
    switch (context) {
      case FILE:
        return Context.FILE;
      case CLASS:
        return Context.CLASS;
      case CODE_BLOCK:
        return Context.CODE_BLOCK;
      case ANNOTATION_INTERFACE:
        return Context.ANNOTATION_INTERFACE;
      case JSHELL:
        return Context.JSHELL;
      default:
        throw new UnsupportedOperationException();
    }
  }

  public PsiBuilder.Marker parse(@NotNull PsiBuilder builder, Context context) {
    return super.parse(builder, toThinContext(context));
  }

  private static BaseContext toThinContext(Context context) {
    switch (context) {
      case FILE:
        return BaseContext.FILE;
      case CLASS:
        return BaseContext.CLASS;
      case CODE_BLOCK:
        return BaseContext.CODE_BLOCK;
      case ANNOTATION_INTERFACE:
        return BaseContext.ANNOTATION_INTERFACE;
      case JSHELL:
        return BaseContext.JSHELL;
      default:
        throw new UnsupportedOperationException();
    }
  }
}