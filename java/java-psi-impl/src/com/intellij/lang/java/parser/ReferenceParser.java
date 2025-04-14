// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.parser;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Use the new Java syntax library instead.
 *             See {@link com.intellij.java.syntax.parser.JavaParser}
 */
@Deprecated
public class ReferenceParser extends BasicReferenceParser {

  /** @deprecated use {@link BasicReferenceParser#EAT_LAST_DOT} instead **/
  @Deprecated
  public static final int EAT_LAST_DOT = 0x01;
  /** @deprecated use {@link BasicReferenceParser#ELLIPSIS} instead **/
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  public static final int ELLIPSIS = 0x02;
  /** @deprecated use {@link BasicReferenceParser#WILDCARD} instead **/
  @Deprecated
  public static final int WILDCARD = 0x04;
  /** @deprecated use {@link BasicReferenceParser#DIAMONDS} instead **/
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  public static final int DIAMONDS = 0x08;
  /** @deprecated use {@link BasicReferenceParser#DISJUNCTIONS} instead **/
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  public static final int DISJUNCTIONS = 0x10;
  /** @deprecated use {@link BasicReferenceParser#CONJUNCTIONS} instead **/
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  public static final int CONJUNCTIONS = 0x20;
  /** @deprecated use {@link BasicReferenceParser#INCOMPLETE_ANNO} instead **/
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  public static final int INCOMPLETE_ANNO = 0x40;
  /** @deprecated use {@link BasicReferenceParser#VAR_TYPE} instead **/
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  public static final int VAR_TYPE = 0x80;


  public static class TypeInfo extends BasicReferenceParser.TypeInfo {
  }

  //for backward compatibility
  @Override
  public ReferenceParser.TypeInfo parseTypeInfo(PsiBuilder builder, int flags) {
    BasicReferenceParser.TypeInfo typeInfo = super.parseTypeInfo(builder, flags);
    if (typeInfo == null) {
      return null;
    }
    TypeInfo info = new TypeInfo();
    info.isPrimitive = typeInfo.isPrimitive;
    info.isParameterized = typeInfo.isParameterized;
    info.isArray = typeInfo.isArray;
    info.isVarArg = typeInfo.isVarArg;
    info.hasErrors = typeInfo.hasErrors;
    info.marker = typeInfo.marker;
    return info;
  }

  public ReferenceParser(@NotNull JavaParser javaParser) {
    super(javaParser);
  }
}
