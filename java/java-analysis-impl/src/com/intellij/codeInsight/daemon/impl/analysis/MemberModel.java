// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.util.ObjectUtils.tryCast;

public class MemberModel {

  private final MemberType myMemberType;
  private final TextRange myTextRange;

  private MemberModel(@NotNull TextRange textRange, @NotNull MemberType memberType) {
    myTextRange = textRange;
    myMemberType = memberType;
  }

  public @NotNull MemberType memberType() {
    return myMemberType;
  }

  public @NotNull TextRange textRange() {
    return myTextRange;
  }

  public static @Nullable MemberModel create(@NotNull PsiErrorElement errorElement) {
    if (inClass(errorElement) || hasClassesWithUnclosedBraces(errorElement.getContainingFile())) return null;
    List<PsiElement> children = new ArrayList<>();
    PsiElement prevSibling = errorElement.getPrevSibling();
    while (isMemberPart(prevSibling)) {
      if (prevSibling instanceof PsiErrorElement) {
        StreamEx.ofReversed(prevSibling.getChildren()).forEach(c -> children.add(c));
      }
      else {
        children.add(prevSibling);
        if (prevSibling instanceof PsiField || prevSibling instanceof PsiMethod) break;
      }
      prevSibling = prevSibling.getPrevSibling();
    }
    Collections.reverse(children);
    Collections.addAll(children, errorElement.getChildren());
    return new MemberParser(ContainerUtil.filter(children, c -> !isWsOrComment(c))).parse();
  }

  private static boolean inClass(@NotNull PsiErrorElement psiErrorElement) {
    PsiClass psiClass = PsiTreeUtil.getParentOfType(psiErrorElement, PsiClass.class);
    if (psiClass == null) return false;
    PsiElement lBrace = psiClass.getLBrace();
    if (lBrace == null) return true;
    PsiElement rBrace = psiClass.getRBrace();
    if (rBrace == null) return true;
    TextRange errorRange = psiErrorElement.getTextRange();
    return lBrace.getTextOffset() <= errorRange.getStartOffset() &&
           rBrace.getTextOffset() + 1 >= errorRange.getEndOffset();
  }

  private static boolean hasClassesWithUnclosedBraces(@Nullable PsiFile psiFile) {
    PsiJavaFile javaFile = tryCast(psiFile, PsiJavaFile.class);
    return javaFile == null || ContainerUtil.exists(javaFile.getClasses(), c -> c.getRBrace() == null);
  }

  private static boolean isWsOrComment(@NotNull PsiElement psiElement) {
    return psiElement instanceof PsiWhiteSpace || psiElement instanceof PsiComment;
  }

  private static boolean isMemberPart(@Nullable PsiElement prevSibling) {
    if (prevSibling == null || prevSibling instanceof PsiPackageStatement || prevSibling instanceof PsiImportList) return false;
    PsiJavaToken token = tryCast(prevSibling, PsiJavaToken.class);
    if (token == null) return true;
    IElementType tokenType = token.getTokenType();
    return tokenType != JavaTokenType.RBRACE;
  }

  public enum MemberType {
    METHOD {
      @Override
      public @NotNull PsiMember create(@NotNull PsiElementFactory factory,
                                       @NotNull String text,
                                       @Nullable PsiElement context) {
        return factory.createMethodFromText(text, context);
      }
    },
    FIELD {
      @Override
      public @NotNull PsiMember create(@NotNull PsiElementFactory factory,
                                       @NotNull String text,
                                       @Nullable PsiElement context) {
        return factory.createFieldFromText(text, context);
      }
    };

    public abstract @NotNull PsiMember create(@NotNull PsiElementFactory factory, @NotNull String text, @Nullable PsiElement context);
  }

  private static class MemberParser {

    private final List<PsiElement> myChildren;
    private int pos;

    private MemberParser(@NotNull List<PsiElement> children) {
      myChildren = children;
    }

    private MemberModel parse() {
      PsiElement startElement = nextChild();
      PsiElement element = parseModifiers(startElement);
      if (element == null) return null;
      PsiJavaToken token = tryCast(parseModifiers(parseTypeParams(element)), PsiJavaToken.class);
      if (token == null) {
        MemberType memberType = parseMemberType(element);
        return memberType == null ? null : new MemberModel(textRange(startElement, element), memberType);
      }
      boolean hasTypeParams = token != element;
      token = parseType(token);
      if (token == null) return null;
      token = parseIdentifier(token);
      if (token == null) return null;
      token = tryCast(nextChild(), PsiJavaToken.class);
      if (token == null) return null;
      if (!hasTypeParams) {
        PsiJavaToken endElement = parseField(token);
        if (endElement != null) {
          return new MemberModel(textRange(startElement, endElement), MemberType.FIELD);
        }
      }
      PsiJavaToken endElement = parseMethod(token);
      if (endElement != null) {
        return new MemberModel(textRange(startElement, endElement), MemberType.METHOD);
      }
      return null;
    }

    private @Nullable PsiJavaToken parseMethod(@NotNull PsiJavaToken token) {
      // void foo() @MyAnno {} is accepted
      token = tryCast(parseModifiers(parseParams(token)), PsiJavaToken.class);
      if (token == null) return null;
      token = parseArrayType(token);
      if (token == null) return null;
      token = parseThrowsClause(token);
      if (token == null) return null;
      IElementType tokenType = token.getTokenType();
      if (tokenType == JavaTokenType.SEMICOLON) return token;
      if (tokenType != JavaTokenType.LBRACE) return null;
      return findClosingBracket(token, JavaTokenType.LBRACE, JavaTokenType.RBRACE);
    }

    private @Nullable PsiJavaToken parseThrowsClause(@Nullable PsiJavaToken token) {
      if (!(token instanceof PsiKeyword) || !JavaKeywords.THROWS.equals(token.getText())) return token;
      token = tryCast(nextChild(), PsiIdentifier.class);
      if (token == null) return null;
      token = tryCast(parseQualifiedType(nextChild()), PsiJavaToken.class);
      while (token != null && token.getTokenType() == JavaTokenType.COMMA) {
        token = tryCast(nextChild(), PsiIdentifier.class);
        if (token == null) return null;
        token = tryCast(parseQualifiedType(nextChild()), PsiJavaToken.class);
      }
      return token;
    }

    private @Nullable PsiElement parseParams(@NotNull PsiJavaToken child) {
      if (child.getTokenType() != JavaTokenType.LPARENTH) return null;
      PsiJavaToken closingBracket = findClosingBracket(child, JavaTokenType.LPARENTH, JavaTokenType.RPARENTH);
      if (closingBracket == null) return null;
      return nextChild();
    }

    private @Nullable PsiElement parseModifiers(@Nullable PsiElement child) {
      return child instanceof PsiModifierList ? nextChild() : child;
    }

    private @Nullable PsiElement parseTypeParams(@Nullable PsiElement element) {
      return element instanceof PsiTypeParameterList ? nextChild() : element;
    }

    private @Nullable PsiJavaToken parseType(@NotNull PsiJavaToken child) {
      IElementType tokenType = child.getTokenType();
      boolean isPrimitiveType = ElementType.PRIMITIVE_TYPE_BIT_SET.contains(tokenType);
      if (!isPrimitiveType && tokenType != JavaTokenType.IDENTIFIER) return null;
      PsiElement element = nextChild();
      if (!isPrimitiveType) {
        element = parseQualifiedType(element);
      }
      else {
        // in case if we have array type e.g. int @Nullable []
        // now we accept invalid declarations like int @Nullable foo() {} but it's fine since we don't need precise parser 
        element = parseModifiers(element);
      }
      return parseArrayType(tryCast(element, PsiJavaToken.class));
    }

    private @Nullable PsiElement parseQualifiedType(@Nullable PsiElement element) {
      element = parseTypeParams(parseModifiers(element));
      PsiJavaToken javaToken = tryCast(element, PsiJavaToken.class);
      while (javaToken != null && javaToken.getTokenType() == JavaTokenType.DOT) {
        element = nextChild();
        element = parseModifiers(element);
        if (!(element instanceof PsiIdentifier)) return null;
        element = nextChild();
        // e.g. Foo.Bar<String>.Baz
        // modifiers actually are not allowed in front of type parameters, but java parser generates empty modifiers list anyway
        element = parseTypeParams(parseModifiers(element));
        javaToken = tryCast(element, PsiJavaToken.class);
      }
      return element;
    }

    private @Nullable PsiJavaToken nextJavaToken() {
      PsiElement child;
      do {
        child = nextChild();
      }
      while (child != null && !(child instanceof PsiJavaToken));
      return (PsiJavaToken)child;
    }

    private @Nullable PsiElement nextChild() {
      if (pos >= myChildren.size()) return null;
      PsiElement child = myChildren.get(pos);
      pos++;
      return child;
    }

    private @Nullable PsiJavaToken findClosingBracket(@NotNull PsiElement child,
                                                      @NotNull IElementType openBracket,
                                                      @NotNull IElementType closeBracket) {
      int depth = 0;
      do {
        PsiJavaToken token = tryCast(child, PsiJavaToken.class);
        if (token != null) {
          IElementType tokenType = token.getTokenType();
          if (tokenType == openBracket) {
            depth++;
          }
          else if (tokenType == closeBracket) {
            depth--;
          }
          if (depth == 0) break;
        }
        child = nextChild();
      }
      while (child != null);

      return tryCast(child, PsiJavaToken.class);
    }

    private @Nullable PsiJavaToken parseField(@NotNull PsiJavaToken token) {
      token = parseArrayType(token);
      if (token == null) return null;
      IElementType tokenType = token.getTokenType();
      if (tokenType == JavaTokenType.SEMICOLON) return token;
      if (tokenType != JavaTokenType.EQ) return null;
      while (tokenType != JavaTokenType.SEMICOLON) {
        if (tokenType == JavaTokenType.LBRACE) {
          token = findClosingBracket(token, JavaTokenType.LBRACE, JavaTokenType.RBRACE);
          if (token == null) return null;
        }
        token = nextJavaToken();
        if (token == null) break;
        tokenType = token.getTokenType();
      }
      return token;
    }

    private @Nullable PsiJavaToken parseArrayType(@Nullable PsiJavaToken token) {
      while (token != null && token.getTokenType() == JavaTokenType.LBRACKET) {
        token = tryCast(nextChild(), PsiJavaToken.class);
        if (token == null || token.getTokenType() != JavaTokenType.RBRACKET) return null;
        token = tryCast(parseModifiers(nextChild()), PsiJavaToken.class);
      }
      return token;
    }

    private static @Nullable MemberType parseMemberType(@NotNull PsiElement element) {
      if (element instanceof PsiMethod) return MemberType.METHOD;
      return element instanceof PsiField ? MemberType.FIELD : null;
    }

    private static @NotNull TextRange textRange(@NotNull PsiElement startElement, @NotNull PsiElement endElement) {
      int start = startElement.getTextRange().getStartOffset();
      int end = endElement.getTextRange().getEndOffset();
      return new TextRange(start, end);
    }

    private static @Nullable PsiJavaToken parseIdentifier(@NotNull PsiJavaToken child) {
      IElementType tokenType = child.getTokenType();
      return tokenType == JavaTokenType.IDENTIFIER ? child : null;
    }
  }
}
