// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DeclarationParser extends BasicDeclarationParser {
  public enum Context {
    FILE, CLASS, CODE_BLOCK, ANNOTATION_INTERFACE
  }

  public DeclarationParser(@NotNull final JavaParser javaParser) {
    super(javaParser);
  }

  //for backward compatibility
  @Nullable
  @Override
  public PsiBuilder.Marker parse(@NotNull PsiBuilder builder, BaseContext context) {
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
      default:
        throw new UnsupportedOperationException();
    }
  }
}