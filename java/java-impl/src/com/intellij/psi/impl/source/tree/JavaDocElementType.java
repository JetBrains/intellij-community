/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTNode;
import com.intellij.lang.StdLanguages;
import com.intellij.lexer.JavaLexer;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.javadoc.PsiDocCommentImpl;
import com.intellij.psi.impl.source.javadoc.PsiDocMethodOrFieldRef;
import com.intellij.psi.impl.source.javadoc.PsiDocParamRef;
import com.intellij.psi.impl.source.javadoc.PsiDocTagImpl;
import com.intellij.psi.impl.source.parsing.JavaParsingContext;
import com.intellij.psi.impl.source.tree.java.PsiInlineDocTagImpl;
import com.intellij.psi.tree.*;
import com.intellij.psi.tree.java.IJavaDocElementType;
import com.intellij.util.CharTable;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;

public interface JavaDocElementType {
  class JavaDocCompositeElementType extends IJavaDocElementType implements ICompositeElementType {
    private final Constructor<? extends ASTNode> myConstructor;

    private JavaDocCompositeElementType(@NonNls final String debugName, final Class<? extends ASTNode> nodeClass) {
      super(debugName);
      myConstructor = ReflectionUtil.getDefaultConstructor(nodeClass);
    }

    @NotNull
    @Override
    public ASTNode createCompositeNode() {
      return ReflectionUtil.createInstance(myConstructor);
    }
  }

  class JavaDocLazyElementType extends ILazyParseableElementType {
    private JavaDocLazyElementType(@NonNls final String debugName) {
      super(debugName, StdLanguages.JAVA);
    }

    @Override
    public ASTNode createNode(final CharSequence text) {
      return new LazyParseablePsiElement(this, text);
    }
  }

  IElementType DOC_TAG = new JavaDocCompositeElementType("DOC_TAG", PsiDocTagImpl.class);
  IElementType DOC_INLINE_TAG = new JavaDocCompositeElementType("DOC_INLINE_TAG", PsiInlineDocTagImpl.class);
  IElementType DOC_METHOD_OR_FIELD_REF = new JavaDocCompositeElementType("DOC_METHOD_OR_FIELD_REF", PsiDocMethodOrFieldRef.class);
  IElementType DOC_PARAMETER_REF = new JavaDocCompositeElementType("DOC_PARAMETER_REF", PsiDocParamRef.class);

  ILazyParseableElementType DOC_REFERENCE_HOLDER = new JavaDocLazyElementType("DOC_REFERENCE_HOLDER") {
    public ASTNode parseContents(final ASTNode chameleon) {
      final CharSequence chars = chameleon.getChars();
      final PsiElement psi = chameleon.getTreeParent().getPsi();
      assert psi != null : chameleon;
      final PsiManager manager = psi.getManager();
      final CharTable table = SharedImplUtil.findCharTableByTree(chameleon);
      //no language features from higher java language versions are present in javadoc
      final JavaParsingContext context = new JavaParsingContext(table, LanguageLevel.JDK_1_3);
      return context.getJavadocParsing().parseJavaDocReference(chars, new JavaLexer(LanguageLevel.JDK_1_3), false, manager);
    }
  };

  ILazyParseableElementType DOC_TYPE_HOLDER = new JavaDocLazyElementType("DOC_TYPE_HOLDER") {
    public ASTNode parseContents(final ASTNode chameleon) {
      final CharSequence chars = chameleon.getChars();
      final PsiElement psi = chameleon.getTreeParent().getPsi();
      assert psi != null : chameleon;
      final PsiManager manager = psi.getManager();
      final CharTable table = SharedImplUtil.findCharTableByTree(chameleon);
      //no language features from higher java language versions are present in javadoc
      final JavaParsingContext context = new JavaParsingContext(table, LanguageLevel.JDK_1_3);
      return context.getJavadocParsing().parseJavaDocReference(chars, new JavaLexer(LanguageLevel.JDK_1_3), true, manager);
    }
  };

  ILazyParseableElementType DOC_COMMENT = new IReparseableElementType("DOC_COMMENT", StdLanguages.JAVA) {
    @Override
    public ASTNode createNode(final CharSequence text) {
      return new PsiDocCommentImpl(text);
    }

    public ASTNode parseContents(final ASTNode chameleon) {
      final CharSequence chars = chameleon.getChars();
      final PsiElement psi = chameleon.getTreeParent().getPsi();
      assert psi != null : chameleon;
      final PsiManager manager = psi.getManager();
      //no higher java language level features are allowed in javadoc
      final JavaParsingContext context = new JavaParsingContext(SharedImplUtil.findCharTableByTree(chameleon), LanguageLevel.JDK_1_3);
      return context.getJavadocParsing().parseDocCommentText(manager, chars, 0, chars.length());
    }

    public boolean isParsable(final CharSequence buffer, final Project project) {
      final JavaLexer lexer = new JavaLexer(LanguageLevel.JDK_1_5);
      lexer.start(buffer);
      if (lexer.getTokenType() != DOC_COMMENT) return false;
      lexer.advance();
      if (lexer.getTokenType() != null) return false;
      return true;
    }
  };
  
  TokenSet ALL_JAVADOC_ELEMENTS = TokenSet.create(
   DOC_TAG, DOC_INLINE_TAG, DOC_METHOD_OR_FIELD_REF, DOC_PARAMETER_REF, DOC_REFERENCE_HOLDER, DOC_TYPE_HOLDER, DOC_COMMENT
  );
}
