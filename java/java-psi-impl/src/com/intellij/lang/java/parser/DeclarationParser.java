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

  @Override
  public void parseClassBodyWithBraces(PsiBuilder builder, boolean isAnnotation, boolean isEnum) {
    super.parseClassBodyWithBraces(builder, isAnnotation, isEnum);
  }

  @Override
  public @Nullable PsiBuilder.Marker parseEnumConstant(PsiBuilder builder) {
    return super.parseEnumConstant(builder);
  }

  @Override
  public void parseClassBodyDeclarations(PsiBuilder builder, boolean isAnnotation) {
    super.parseClassBodyDeclarations(builder, isAnnotation);
  }

  @Override
  public @NotNull Pair<PsiBuilder.Marker, Boolean> parseModifierList(PsiBuilder builder) {
    return super.parseModifierList(builder);
  }

  @Override
  public @NotNull Pair<PsiBuilder.Marker, Boolean> parseModifierList(PsiBuilder builder, TokenSet modifiers) {
    return super.parseModifierList(builder, modifiers);
  }

  @Override
  public void parseParameterList(PsiBuilder builder) {
    super.parseParameterList(builder);
  }

  @Override
  public void parseResourceList(PsiBuilder builder) {
    super.parseResourceList(builder);
  }

  @Override
  public void parseLambdaParameterList(PsiBuilder builder, boolean typed) {
    super.parseLambdaParameterList(builder, typed);
  }

  @Override
  public @Nullable PsiBuilder.Marker parseParameter(PsiBuilder builder, boolean ellipsis, boolean disjunctiveType, boolean varType) {
    return super.parseParameter(builder, ellipsis, disjunctiveType, varType);
  }

  @Override
  public @Nullable PsiBuilder.Marker parseParameterOrRecordComponent(PsiBuilder builder,
                                                                     boolean ellipsis,
                                                                     boolean disjunctiveType,
                                                                     boolean varType,
                                                                     boolean isParameter) {
    return super.parseParameterOrRecordComponent(builder, ellipsis, disjunctiveType, varType, isParameter);
  }

  @Override
  public @Nullable PsiBuilder.Marker parseResource(PsiBuilder builder) {
    return super.parseResource(builder);
  }

  @Override
  public @Nullable PsiBuilder.Marker parseLambdaParameter(PsiBuilder builder, boolean typed) {
    return super.parseLambdaParameter(builder, typed);
  }

  @Override
  public @Nullable PsiBuilder.Marker parseAnnotations(PsiBuilder builder) {
    return super.parseAnnotations(builder);
  }

  @Override
  public @NotNull PsiBuilder.Marker parseAnnotation(PsiBuilder builder) {
    return super.parseAnnotation(builder);
  }

  @Override
  public void parseAnnotationValue(PsiBuilder builder) {
    super.parseAnnotationValue(builder);
  }
}