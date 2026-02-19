// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.parser;

import com.intellij.core.JavaPsiBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.lang.PsiBuilder;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.lang.PsiBuilderUtil.expect;
import static com.intellij.lang.java.parser.JavaParserUtil.emptyElement;
import static com.intellij.lang.java.parser.JavaParserUtil.error;
import static com.intellij.lang.java.parser.JavaParserUtil.expectOrError;
import static com.intellij.lang.java.parser.JavaParserUtil.getLanguageLevel;
import static com.intellij.psi.impl.source.tree.JavaElementType.DIAMOND_TYPE;
import static com.intellij.psi.impl.source.tree.JavaElementType.EXTENDS_BOUND_LIST;
import static com.intellij.psi.impl.source.tree.JavaElementType.IMPORT_STATIC_REFERENCE;
import static com.intellij.psi.impl.source.tree.JavaElementType.JAVA_CODE_REFERENCE;
import static com.intellij.psi.impl.source.tree.JavaElementType.REFERENCE_PARAMETER_LIST;
import static com.intellij.psi.impl.source.tree.JavaElementType.TYPE;
import static com.intellij.psi.impl.source.tree.JavaElementType.TYPE_PARAMETER;
import static com.intellij.psi.impl.source.tree.JavaElementType.TYPE_PARAMETER_LIST;
import static com.intellij.util.BitUtil.isSet;
import static com.intellij.util.BitUtil.set;

/**
 * @deprecated Use the new Java syntax library instead.
 * See {@link com.intellij.java.syntax.parser.JavaParser}
 */
@Deprecated
public class ReferenceParser {
  public static final int EAT_LAST_DOT = 0x01;
  public static final int ELLIPSIS = 0x02;
  public static final int WILDCARD = 0x04;
  public static final int DIAMONDS = 0x08;
  public static final int DISJUNCTIONS = 0x10;
  public static final int CONJUNCTIONS = 0x20;
  public static final int INCOMPLETE_ANNO = 0x40;
  public static final int VAR_TYPE = 0x80;
  private static final TokenSet WILDCARD_KEYWORD_SET = TokenSet.create(JavaTokenType.EXTENDS_KEYWORD, JavaTokenType.SUPER_KEYWORD);
  private final JavaParser myParser;

  public @Nullable PsiBuilder.Marker parseType(PsiBuilder builder, int flags) {
    TypeInfo typeInfo = parseTypeInfo(builder, flags);
    return typeInfo != null ? typeInfo.marker : null;
  }

  private @Nullable TypeInfo parseTypeInfo(PsiBuilder builder, int flags, boolean badWildcard) {
    if (builder.getTokenType() == null) return null;

    TypeInfo typeInfo = new TypeInfo();

    PsiBuilder.Marker type = builder.mark();
    PsiBuilder.Marker anno = myParser.getDeclarationParser().parseAnnotations(builder);

    IElementType tokenType = builder.getTokenType();
    if (tokenType == JavaTokenType.IDENTIFIER &&
        isSet(flags, VAR_TYPE) &&
        builder.lookAhead(1) != JavaTokenType.DOT &&
        builder.lookAhead(1) != JavaTokenType.COLON &&
        JavaKeywords.VAR.equals(builder.getTokenText()) &&
        JavaFeature.LVTI.isSufficient(getLanguageLevel(builder))) {
      builder.remapCurrentToken(tokenType = JavaTokenType.VAR_KEYWORD);
    }
    else if (tokenType == JavaTokenType.VAR_KEYWORD && !isSet(flags, VAR_TYPE)) {
      builder.remapCurrentToken(tokenType = JavaTokenType.IDENTIFIER);
    }

    if (expect(builder, ElementType.PRIMITIVE_TYPE_BIT_SET)) {
      typeInfo.isPrimitive = true;
    }
    else if ((isSet(flags, WILDCARD) || badWildcard) && (tokenType == JavaTokenType.QUEST)) {
      builder.advanceLexer();
      completeWildcardType(builder, isSet(flags, WILDCARD), type);
      typeInfo.marker = type;
      return typeInfo;
    }
    else if (tokenType == JavaTokenType.IDENTIFIER) {
      parseJavaCodeReference(builder, isSet(flags, EAT_LAST_DOT), true, false, false, false, isSet(flags, DIAMONDS), typeInfo);
    }
    else if (tokenType == JavaTokenType.VAR_KEYWORD) {
      builder.advanceLexer();
      type.done(TYPE);
      typeInfo.marker = type;
      return typeInfo;
    }
    else if (isSet(flags, DIAMONDS) && tokenType == JavaTokenType.GT) {
      if (anno == null) {
        emptyElement(builder, DIAMOND_TYPE);
      }
      else {
        error(builder, JavaPsiBundle.message("expected.identifier"));
        typeInfo.hasErrors = true;
      }
      type.done(TYPE);
      typeInfo.marker = type;
      return typeInfo;
    }
    else {
      type.drop();
      if (anno != null && isSet(flags, INCOMPLETE_ANNO)) {
        error(builder, JavaPsiBundle.message("expected.type"));
        typeInfo.marker = anno;
        typeInfo.hasErrors = true;
        return typeInfo;
      }
      return null;
    }

    type.done(TYPE);
    while (true) {
      myParser.getDeclarationParser().parseAnnotations(builder);

      PsiBuilder.Marker bracket = builder.mark();
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
    }

    if (isSet(flags, ELLIPSIS) && builder.getTokenType() == JavaTokenType.ELLIPSIS) {
      builder.advanceLexer();
      typeInfo.isVarArg = true;
    }

    if (typeInfo.isVarArg || typeInfo.isArray) {
      type = type.precede();
      type.done(TYPE);
    }

    typeInfo.marker = type;
    return typeInfo;
  }

  private void completeWildcardType(PsiBuilder builder, boolean wildcard, PsiBuilder.Marker type) {
    if (expect(builder, WILDCARD_KEYWORD_SET)) {
      if (parseTypeInfo(builder, EAT_LAST_DOT) == null) {
        error(builder, JavaPsiBundle.message("expected.type"));
      }
    }

    if (wildcard) {
      type.done(TYPE);
    }
    else {
      type.error(JavaPsiBundle.message("error.message.wildcard.not.expected"));
    }
  }

  public @Nullable PsiBuilder.Marker parseJavaCodeReference(PsiBuilder builder,
                                                            boolean eatLastDot,
                                                            boolean parameterList,
                                                            boolean isNew,
                                                            boolean diamonds) {
    return parseJavaCodeReference(builder, eatLastDot, parameterList, false, false, isNew, diamonds, new TypeInfo());
  }

  public boolean parseImportCodeReference(PsiBuilder builder, boolean isStatic) {
    TypeInfo typeInfo = new TypeInfo();
    parseJavaCodeReference(builder, true, false, true, isStatic, false, false, typeInfo);
    return !typeInfo.hasErrors;
  }

  private @Nullable PsiBuilder.Marker parseJavaCodeReference(PsiBuilder builder,
                                                             boolean eatLastDot,
                                                             boolean parameterList,
                                                             boolean isImport,
                                                             boolean isStaticImport,
                                                             boolean isNew,
                                                             boolean diamonds,
                                                             TypeInfo typeInfo) {
    PsiBuilder.Marker refElement = builder.mark();

    myParser.getDeclarationParser().parseAnnotations(builder);

    if (!expect(builder, JavaTokenType.IDENTIFIER)) {
      refElement.rollbackTo();
      if (isImport) {
        error(builder, JavaPsiBundle.message("expected.identifier"));
      }
      typeInfo.hasErrors = true;
      return null;
    }

    if (parameterList) {
      typeInfo.isParameterized = parseReferenceParameterList(builder, true, diamonds);
    }
    else if (!isStaticImport) {
      emptyElement(builder, REFERENCE_PARAMETER_LIST);
    }

    while (builder.getTokenType() == JavaTokenType.DOT) {
      refElement.done(JAVA_CODE_REFERENCE);

      if (isNew && !diamonds && typeInfo.isParameterized) {
        return refElement;
      }

      PsiBuilder.Marker dotPos = builder.mark();
      builder.advanceLexer();

      myParser.getDeclarationParser().parseAnnotations(builder);

      if (isImport && expect(builder, JavaTokenType.ASTERISK)) {
        dotPos.drop();
        return refElement;
      }
      else if (!expect(builder, JavaTokenType.IDENTIFIER)) {
        if (!eatLastDot) {
          dotPos.rollbackTo();
          return refElement;
        }

        typeInfo.hasErrors = true;
        if (isImport) {
          error(builder, JavaPsiBundle.message("import.statement.identifier.or.asterisk.expected."));
        }
        else {
          error(builder, JavaPsiBundle.message("expected.identifier"));
        }
        dotPos.drop();
        return refElement;
      }
      dotPos.drop();

      refElement = refElement.precede();

      if (parameterList) {
        typeInfo.isParameterized = parseReferenceParameterList(builder, true, diamonds);
      }
      else {
        emptyElement(builder, REFERENCE_PARAMETER_LIST);
      }
    }

    if (isStaticImport) {
      refElement.done(IMPORT_STATIC_REFERENCE);
    }
    else {
      refElement.done(JAVA_CODE_REFERENCE);
    }
    return refElement;
  }

  public boolean parseReferenceParameterList(PsiBuilder builder, boolean wildcard, boolean diamonds) {
    PsiBuilder.Marker list = builder.mark();
    if (!expect(builder, JavaTokenType.LT)) {
      list.done(REFERENCE_PARAMETER_LIST);
      return false;
    }

    int flags = set(set(EAT_LAST_DOT, WILDCARD, wildcard), DIAMONDS, diamonds);
    boolean isOk = true;
    while (true) {
      if (parseTypeInfo(builder, flags, true) == null) {
        error(builder, JavaPsiBundle.message("expected.identifier"));
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

    list.done(REFERENCE_PARAMETER_LIST);
    return isOk;
  }

  public @NotNull PsiBuilder.Marker parseTypeParameters(PsiBuilder builder) {
    PsiBuilder.Marker list = builder.mark();
    if (!expect(builder, JavaTokenType.LT)) {
      list.done(TYPE_PARAMETER_LIST);
      return list;
    }

    do {
      PsiBuilder.Marker param = parseTypeParameter(builder);
      if (param == null) {
        error(builder, JavaPsiBundle.message("expected.type.parameter"));
      }
    }
    while (expect(builder, JavaTokenType.COMMA));

    if (!expect(builder, JavaTokenType.GT)) {
      // hack for completion
      if (builder.getTokenType() == JavaTokenType.IDENTIFIER) {
        if (builder.lookAhead(1) == JavaTokenType.GT) {
          PsiBuilder.Marker errorElement = builder.mark();
          builder.advanceLexer();
          errorElement.error(JavaPsiBundle.message("unexpected.identifier"));
          builder.advanceLexer();
        }
        else {
          error(builder, JavaPsiBundle.message("expected.gt"));
        }
      }
      else {
        error(builder, JavaPsiBundle.message("expected.gt"));
      }
    }

    list.done(TYPE_PARAMETER_LIST);
    return list;
  }

  public @Nullable PsiBuilder.Marker parseTypeParameter(PsiBuilder builder) {
    PsiBuilder.Marker param = builder.mark();

    myParser.getDeclarationParser().parseAnnotations(builder);

    boolean wild = expect(builder, JavaTokenType.QUEST);
    if (!wild && !expect(builder, JavaTokenType.IDENTIFIER)) {
      param.rollbackTo();
      return null;
    }

    parseReferenceList(builder, JavaTokenType.EXTENDS_KEYWORD, EXTENDS_BOUND_LIST, JavaTokenType.AND);

    if (!wild) {
      param.done(TYPE_PARAMETER);
    }
    else {
      param.error(JavaPsiBundle.message("error.message.wildcard.not.expected"));
    }
    return param;
  }

  public boolean parseReferenceList(PsiBuilder builder, IElementType start, @Nullable IElementType type, IElementType delimiter) {
    PsiBuilder.Marker element = builder.mark();

    boolean endsWithError = false;
    if (expect(builder, start)) {
      do {
        endsWithError = false;
        PsiBuilder.Marker classReference = parseJavaCodeReference(builder, false, true, false, false);
        if (classReference == null) {
          error(builder, JavaPsiBundle.message("expected.identifier"));
          endsWithError = true;
        }
      }
      while (expect(builder, delimiter));
    }

    if (type != null) {
      element.done(type);
    }
    else {
      element.error(JavaPsiBundle.message("bound.not.expected"));
    }
    return endsWithError;
  }

  //for backward compatibility
  @Nullable
  public ReferenceParser.TypeInfo parseTypeInfo(PsiBuilder builder, int flags) {
    ReferenceParser.TypeInfo typeInfo1 = parseTypeInfo(builder, flags, false);

    if (typeInfo1 != null) {
      assert !isSet(flags, DISJUNCTIONS) || !isSet(flags, CONJUNCTIONS) : "don't set both flags simultaneously";
      IElementType operator = isSet(flags, DISJUNCTIONS) ? JavaTokenType.OR : isSet(flags, CONJUNCTIONS) ? JavaTokenType.AND : null;

      if (operator != null && builder.getTokenType() == operator) {
        typeInfo1.marker = typeInfo1.marker.precede();

        while (builder.getTokenType() == operator) {
          builder.advanceLexer();
          IElementType tokenType = builder.getTokenType();
          if (tokenType != JavaTokenType.IDENTIFIER && tokenType != JavaTokenType.AT) {
            error(builder, JavaPsiBundle.message("expected.identifier"));
          }
          parseTypeInfo(builder, flags, false);
        }

        typeInfo1.marker.done(TYPE);
      }
    }

    TypeInfo typeInfo = typeInfo1;
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
    this.myParser = javaParser;
  }

  public static class TypeInfo {
    public boolean isPrimitive;
    public boolean isParameterized;
    public boolean isArray;
    public boolean isVarArg;
    public boolean hasErrors;
    public PsiBuilder.Marker marker;
  }
}
