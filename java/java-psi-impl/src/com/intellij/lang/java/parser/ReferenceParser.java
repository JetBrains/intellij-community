/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.lang.PsiBuilder;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.lang.PsiBuilderUtil.expect;
import static com.intellij.lang.java.parser.JavaParserUtil.*;
import static com.intellij.util.BitUtil.isSet;
import static com.intellij.util.BitUtil.set;

public class ReferenceParser {
  public static final int EAT_LAST_DOT = 0x01;
  public static final int ELLIPSIS = 0x02;
  public static final int WILDCARD = 0x04;
  public static final int DIAMONDS = 0x08;
  public static final int DISJUNCTIONS = 0x10;
  public static final int CONJUNCTIONS = 0x20;
  public static final int INCOMPLETE_ANNO = 0x40;

  public static class TypeInfo {
    public boolean isPrimitive;
    public boolean isParameterized;
    public boolean isArray;
    public boolean isVarArg;
    public boolean hasErrors;
    public PsiBuilder.Marker marker;
  }

  private static final TokenSet WILDCARD_KEYWORD_SET = TokenSet.create(JavaTokenType.EXTENDS_KEYWORD, JavaTokenType.SUPER_KEYWORD);

  private final JavaParser myParser;

  public ReferenceParser(@NotNull final JavaParser javaParser) {
    myParser = javaParser;
  }

  @Nullable
  public PsiBuilder.Marker parseType(final PsiBuilder builder, final int flags) {
    final TypeInfo typeInfo = parseTypeInfo(builder, flags);
    return typeInfo != null ? typeInfo.marker : null;
  }

  @Nullable
  public TypeInfo parseTypeInfo(final PsiBuilder builder, final int flags) {
    final TypeInfo typeInfo = parseTypeInfo(builder, flags, false);

    if (typeInfo != null) {
      assert !isSet(flags, DISJUNCTIONS) || !isSet(flags,CONJUNCTIONS) : "don't set both flags simultaneously";
      final IElementType operator = isSet(flags, DISJUNCTIONS) ? JavaTokenType.OR : isSet(flags, CONJUNCTIONS) ? JavaTokenType.AND : null;

      if (operator != null && builder.getTokenType() == operator) {
        typeInfo.marker = typeInfo.marker.precede();

        while (builder.getTokenType() == operator) {
          builder.advanceLexer();
          IElementType tokenType = builder.getTokenType();
          if (tokenType != JavaTokenType.IDENTIFIER && tokenType != JavaTokenType.AT) {
            error(builder, JavaErrorMessages.message("expected.identifier"));
          }
          parseTypeInfo(builder, flags, false);
        }

        typeInfo.marker.done(JavaElementType.TYPE);
      }
    }

    return typeInfo;
  }

  @Nullable
  private TypeInfo parseTypeInfo(PsiBuilder builder, int flags, boolean badWildcard) {
    if (builder.getTokenType() == null) return null;

    final TypeInfo typeInfo = new TypeInfo();

    PsiBuilder.Marker type = builder.mark();
    PsiBuilder.Marker anno = myParser.getDeclarationParser().parseAnnotations(builder);

    final IElementType tokenType = builder.getTokenType();
    if (expect(builder, ElementType.PRIMITIVE_TYPE_BIT_SET)) {
      typeInfo.isPrimitive = true;
    }
    else if ((isSet(flags, WILDCARD) || badWildcard) && (tokenType == JavaTokenType.QUEST || isKeywordAny(builder))) {
      if (tokenType == JavaTokenType.QUEST) {
        builder.advanceLexer();
      }
      else {
        dummy(builder);
      }
      completeWildcardType(builder, isSet(flags, WILDCARD), type);
      typeInfo.marker = type;
      return typeInfo;
    }
    else if (tokenType == JavaTokenType.IDENTIFIER) {
      parseJavaCodeReference(builder, isSet(flags, EAT_LAST_DOT), true, false, false, false, isSet(flags, DIAMONDS), typeInfo);
    }
    else if (isSet(flags, DIAMONDS) && tokenType == JavaTokenType.GT) {
      if (anno == null) {
        emptyElement(builder, JavaElementType.DIAMOND_TYPE);
      }
      else {
        error(builder, JavaErrorMessages.message("expected.identifier"));
        typeInfo.hasErrors = true;
      }
      type.done(JavaElementType.TYPE);
      typeInfo.marker = type;
      return typeInfo;
    }
    else {
      type.drop();
      if (anno != null && isSet(flags, INCOMPLETE_ANNO)) {
        error(builder, JavaErrorMessages.message("expected.type"));
        typeInfo.marker = anno;
        typeInfo.hasErrors = true;
        return typeInfo;
      }
      return null;
    }

    while (true) {
      type.done(JavaElementType.TYPE);
      myParser.getDeclarationParser().parseAnnotations(builder);

      final PsiBuilder.Marker bracket = builder.mark();
      if (!expect(builder, JavaTokenType.LBRACKET)) {
        bracket.drop();
        break;
      }
      if (!expect(builder, JavaTokenType.RBRACKET)) {
        bracket.rollbackTo();
        break;
      }
      bracket.drop();
      typeInfo.isArray = true;

      type = type.precede();
    }

    if (isSet(flags, ELLIPSIS) && builder.getTokenType() == JavaTokenType.ELLIPSIS) {
      type = type.precede();
      builder.advanceLexer();
      type.done(JavaElementType.TYPE);
      typeInfo.isVarArg = true;
    }

    typeInfo.marker = type;
    return typeInfo;
  }

  private void completeWildcardType(PsiBuilder builder, boolean wildcard, PsiBuilder.Marker type) {
    if (expect(builder, WILDCARD_KEYWORD_SET)) {
      if (parseTypeInfo(builder, EAT_LAST_DOT) == null) {
        error(builder, JavaErrorMessages.message("expected.type"));
      }
    }

    if (wildcard) {
      type.done(JavaElementType.TYPE);
    }
    else {
      type.error(JavaErrorMessages.message("wildcard.not.expected"));
    }
  }

  @Nullable
  public PsiBuilder.Marker parseJavaCodeReference(PsiBuilder builder, boolean eatLastDot, boolean parameterList, boolean isNew, boolean diamonds) {
    return parseJavaCodeReference(builder, eatLastDot, parameterList, false, false, isNew, diamonds, new TypeInfo());
  }

  public boolean parseImportCodeReference(final PsiBuilder builder, final boolean isStatic) {
    final TypeInfo typeInfo = new TypeInfo();
    parseJavaCodeReference(builder, true, false, true, isStatic, false, false, typeInfo);
    return !typeInfo.hasErrors;
  }

  @Nullable
  private PsiBuilder.Marker parseJavaCodeReference(PsiBuilder builder, boolean eatLastDot, boolean parameterList, boolean isImport,
                                                   boolean isStaticImport, boolean isNew, boolean diamonds, TypeInfo typeInfo) {
    PsiBuilder.Marker refElement = builder.mark();

    myParser.getDeclarationParser().parseAnnotations(builder);

    if (!expect(builder, JavaTokenType.IDENTIFIER)) {
      refElement.rollbackTo();
      if (isImport) {
        error(builder, JavaErrorMessages.message("expected.identifier"));
      }
      typeInfo.hasErrors = true;
      return null;
    }

    if (parameterList) {
      typeInfo.isParameterized = parseReferenceParameterList(builder, true, diamonds);
    }
    else if (!isStaticImport) {
      emptyElement(builder, JavaElementType.REFERENCE_PARAMETER_LIST);
    }

    boolean hasIdentifier;
    while (builder.getTokenType() == JavaTokenType.DOT) {
      refElement.done(JavaElementType.JAVA_CODE_REFERENCE);

      if (isNew && !diamonds && typeInfo.isParameterized) {
        return refElement;
      }

      final PsiBuilder.Marker dotPos = builder.mark();
      builder.advanceLexer();

      myParser.getDeclarationParser().parseAnnotations(builder);

      if (expect(builder, JavaTokenType.IDENTIFIER)) {
        hasIdentifier = true;
      }
      else if (isImport && expect(builder, JavaTokenType.ASTERISK)) {
        dotPos.drop();
        return refElement;
      }
      else {
        if (!eatLastDot) {
          dotPos.rollbackTo();
          return refElement;
        }
        hasIdentifier = false;
      }
      dotPos.drop();

      final PsiBuilder.Marker prevElement = refElement;
      refElement = refElement.precede();

      if (!hasIdentifier) {
        typeInfo.hasErrors = true;
        if (isImport) {
          error(builder, JavaErrorMessages.message("import.statement.identifier.or.asterisk.expected."));
          refElement.drop();
          return prevElement;
        }
        else {
          error(builder, JavaErrorMessages.message("expected.identifier"));
          emptyElement(builder, JavaElementType.REFERENCE_PARAMETER_LIST);
          break;
        }
      }

      if (parameterList) {
        typeInfo.isParameterized = parseReferenceParameterList(builder, true, diamonds);
      }
      else {
        emptyElement(builder, JavaElementType.REFERENCE_PARAMETER_LIST);
      }
    }

    refElement.done(isStaticImport ? JavaElementType.IMPORT_STATIC_REFERENCE : JavaElementType.JAVA_CODE_REFERENCE);
    return refElement;
  }

  public boolean parseReferenceParameterList(final PsiBuilder builder, final boolean wildcard, final boolean diamonds) {
    final PsiBuilder.Marker list = builder.mark();
    if (!expect(builder, JavaTokenType.LT)) {
      list.done(JavaElementType.REFERENCE_PARAMETER_LIST);
      return false;
    }

    int flags = set(set(EAT_LAST_DOT, WILDCARD, wildcard), DIAMONDS, diamonds);
    boolean isOk = true;
    while (true) {
      if (parseTypeInfo(builder, flags, true) == null) {
        error(builder, JavaErrorMessages.message("expected.identifier"));
      }
      else {
        IElementType tokenType = builder.getTokenType();
        if (WILDCARD_KEYWORD_SET.contains(tokenType)) {
          parseReferenceList(builder, tokenType, null, JavaTokenType.AND);
        }
      }

      if (expect(builder, JavaTokenType.GT)) {
        break;
      }
      else if (!expectOrError(builder, JavaTokenType.COMMA, "expected.gt.or.comma")) {
        isOk = false;
        break;
      }
      flags = set(flags, DIAMONDS, false);
    }

    list.done(JavaElementType.REFERENCE_PARAMETER_LIST);
    return isOk;
  }

  @NotNull
  public PsiBuilder.Marker parseTypeParameters(final PsiBuilder builder) {
    final PsiBuilder.Marker list = builder.mark();
    if (!expect(builder, JavaTokenType.LT)) {
      list.done(JavaElementType.TYPE_PARAMETER_LIST);
      return list;
    }

    while (true) {
      final PsiBuilder.Marker param = parseTypeParameter(builder);
      if (param == null) {
        error(builder, JavaErrorMessages.message("expected.type.parameter"));
      }
      if (!expect(builder, JavaTokenType.COMMA)) {
        break;
      }
    }

    if (!expect(builder, JavaTokenType.GT)) {
      // hack for completion
      if (builder.getTokenType() == JavaTokenType.IDENTIFIER) {
        if (builder.lookAhead(1) == JavaTokenType.GT) {
          final PsiBuilder.Marker errorElement = builder.mark();
          builder.advanceLexer();
          errorElement.error(JavaErrorMessages.message("unexpected.identifier"));
          builder.advanceLexer();
        }
        else {
          error(builder, JavaErrorMessages.message("expected.gt"));
        }
      }
      else {
        error(builder, JavaErrorMessages.message("expected.gt"));
      }
    }

    list.done(JavaElementType.TYPE_PARAMETER_LIST);
    return list;
  }

  @Nullable
  public PsiBuilder.Marker parseTypeParameter(final PsiBuilder builder) {
    final PsiBuilder.Marker param = builder.mark();

    myParser.getDeclarationParser().parseAnnotations(builder);

    if (isKeywordAny(builder)) {
      dummy(builder);
    }

    final boolean wild = expect(builder, JavaTokenType.QUEST);
    if (!wild && !expect(builder, JavaTokenType.IDENTIFIER)) {
      param.rollbackTo();
      return null;
    }

    parseReferenceList(builder, JavaTokenType.EXTENDS_KEYWORD, JavaElementType.EXTENDS_BOUND_LIST, JavaTokenType.AND);

    if (!wild) {
      param.done(JavaElementType.TYPE_PARAMETER);
    }
    else {
      param.error(JavaErrorMessages.message("wildcard.not.expected"));
    }
    return param;
  }

  public boolean parseReferenceList(PsiBuilder builder, IElementType start, @Nullable IElementType type, IElementType delimiter) {
    PsiBuilder.Marker element = builder.mark();

    boolean endsWithError = false;
    if (expect(builder, start)) {
      while (true) {
        endsWithError = false;
        PsiBuilder.Marker classReference = parseJavaCodeReference(builder, true, true, false, false);
        if (classReference == null) {
          error(builder, JavaErrorMessages.message("expected.identifier"));
          endsWithError = true;
        }
        if (!expect(builder, delimiter)) {
          break;
        }
      }
    }

    if (type != null) {
      element.done(type);
    }
    else {
      element.error(JavaErrorMessages.message("bound.not.expected"));
    }
    return endsWithError;
  }

  private static boolean isKeywordAny(PsiBuilder builder) {
    return getLanguageLevel(builder).isAtLeast(LanguageLevel.JDK_X) && "any".equals(builder.getTokenText());
  }

  private static void dummy(PsiBuilder builder) {
    PsiBuilder.Marker mark = builder.mark();
    builder.advanceLexer();
    mark.done(JavaElementType.DUMMY_ELEMENT);
  }
}
