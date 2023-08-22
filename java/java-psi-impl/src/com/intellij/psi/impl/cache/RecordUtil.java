// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.cache;

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.lang.LighterASTTokenNode;
import com.intellij.lang.java.lexer.JavaDocLexer;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiClassStub;
import com.intellij.psi.impl.java.stubs.PsiFieldStub;
import com.intellij.psi.impl.java.stubs.PsiModifierListStub;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

public final class RecordUtil {
  private static final String DEPRECATED_ANNOTATION_NAME = "Deprecated";
  private static final String DEPRECATED_TAG = "@deprecated";

  private RecordUtil() { }

  public static boolean isDeprecatedByAnnotation(@NotNull LighterAST tree, @NotNull LighterASTNode modList) {
    for (final LighterASTNode child : tree.getChildren(modList)) {
      if (child.getTokenType() == JavaElementType.ANNOTATION) {
        final LighterASTNode ref = LightTreeUtil.firstChildOfType(tree, child, JavaElementType.JAVA_CODE_REFERENCE);
        if (ref != null) {
          final LighterASTNode id = LightTreeUtil.firstChildOfType(tree, ref, JavaTokenType.IDENTIFIER);
          if (id != null) {
            final String name = intern(tree.getCharTable(), id);
            if (DEPRECATED_ANNOTATION_NAME.equals(name)) return true;
          }
        }
      }
    }

    return false;
  }

  public static boolean isDeprecatedByDocComment(@NotNull LighterAST tree, @NotNull LighterASTNode comment) {
    String text = LightTreeUtil.toFilteredString(tree, comment, null);
    if (text.contains(DEPRECATED_TAG)) {
      JavaDocLexer lexer = new JavaDocLexer(LanguageLevel.HIGHEST);
      lexer.start(text);
      IElementType token;
      while ((token = lexer.getTokenType()) != null) {
        if (token == JavaDocTokenType.DOC_TAG_NAME && DEPRECATED_TAG.equals(lexer.getTokenText())) {
          return true;
        }
        lexer.advance();
      }
    }

    return false;
  }

  public static int packModifierList(@NotNull LighterAST tree, @NotNull LighterASTNode modList) {
    int packed = 0;
    for (final LighterASTNode child : tree.getChildren(modList)) {
      packed |= ModifierFlags.KEYWORD_TO_MODIFIER_FLAG_MAP.getInt(child.getTokenType());
    }
    return packed;
  }

  @NotNull
  public static String intern(@NotNull CharTable table, @NotNull LighterASTNode node) {
    assert node instanceof LighterASTTokenNode : node;
    return table.intern(((LighterASTTokenNode)node).getText()).toString();
  }

  public static boolean isStaticNonPrivateMember(@NotNull StubElement<?> stub) {
    PsiModifierListStub type = stub.findChildStubByType(JavaStubElementTypes.MODIFIER_LIST);
    if (type == null) {
      return false;
    }

    int mask = type.getModifiersMask();
    if (ModifierFlags.hasModifierProperty(PsiModifier.PRIVATE, mask)) {
      return false;
    }

    if (ModifierFlags.hasModifierProperty(PsiModifier.STATIC, mask)) {
      return true;
    }

    return stub instanceof PsiFieldStub &&
           stub.getStubType() == JavaElementType.ENUM_CONSTANT ||
           stub.getParentStub() instanceof PsiClassStub && ((PsiClassStub<?>)stub.getParentStub()).isInterface();
  }
}