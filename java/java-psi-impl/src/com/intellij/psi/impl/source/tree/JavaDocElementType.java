/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.lang.Language;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lang.java.parser.JavaParserUtil;
import com.intellij.lang.java.parser.JavadocParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.impl.source.javadoc.*;
import com.intellij.psi.tree.*;
import com.intellij.psi.tree.java.IJavaDocElementType;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.reflect.ConstructorAccessor;

import java.lang.reflect.Constructor;

public interface JavaDocElementType {
  class JavaDocCompositeElementType extends IJavaDocElementType implements ICompositeElementType {
    private final ConstructorAccessor myConstructor;

    private JavaDocCompositeElementType(@NonNls final String debugName, final Class<? extends ASTNode> nodeClass) {
      super(debugName);
      Constructor<? extends ASTNode> constructor = ReflectionUtil.getDefaultConstructor(nodeClass);
      myConstructor = ReflectionUtil.getConstructorAccessor(constructor);
    }

    @NotNull
    @Override
    public ASTNode createCompositeNode() {
      return ReflectionUtil.createInstanceViaConstructorAccessor(myConstructor);
    }
  }

  class JavaDocLazyElementType extends ILazyParseableElementType {
    private JavaDocLazyElementType(@NonNls final String debugName) {
      super(debugName, JavaLanguage.INSTANCE);
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
  IElementType DOC_TAG_VALUE_ELEMENT = new IJavaDocElementType("DOC_TAG_VALUE_ELEMENT");

  ILazyParseableElementType DOC_REFERENCE_HOLDER = new JavaDocLazyElementType("DOC_REFERENCE_HOLDER") {
    private final JavaParserUtil.ParserWrapper myParser = new JavaParserUtil.ParserWrapper() {
      @Override
      public void parse(final PsiBuilder builder) {
        JavadocParser.parseJavadocReference(builder);
      }
    };

    @Nullable
    @Override
    public ASTNode parseContents(final ASTNode chameleon) {
      return JavaParserUtil.parseFragment(chameleon, myParser, false, LanguageLevel.JDK_1_3);
    }
  };

  ILazyParseableElementType DOC_TYPE_HOLDER = new JavaDocLazyElementType("DOC_TYPE_HOLDER") {
    private final JavaParserUtil.ParserWrapper myParser = new JavaParserUtil.ParserWrapper() {
      @Override
      public void parse(final PsiBuilder builder) {
        JavadocParser.parseJavadocType(builder);
      }
    };

    @Nullable
    @Override
    public ASTNode parseContents(final ASTNode chameleon) {
      return JavaParserUtil.parseFragment(chameleon, myParser, false, LanguageLevel.JDK_1_3);
    }
  };

  ILazyParseableElementType DOC_COMMENT = new IReparseableElementType("DOC_COMMENT", JavaLanguage.INSTANCE) {
    private final JavaParserUtil.ParserWrapper myParser = new JavaParserUtil.ParserWrapper() {
      @Override
      public void parse(final PsiBuilder builder) {
        JavadocParser.parseDocCommentText(builder);
      }
    };

    @Override
    public ASTNode createNode(final CharSequence text) {
      return new PsiDocCommentImpl(text);
    }

    @Nullable
    @Override
    public ASTNode parseContents(final ASTNode chameleon) {
      return JavaParserUtil.parseFragment(chameleon, myParser);
    }

    @Override
    public boolean isParsable(final CharSequence buffer, Language fileLanguage, final Project project) {
      Lexer lexer = JavaParserDefinition.createLexer(LanguageLevelProjectExtension.getInstance(project).getLanguageLevel());
      lexer.start(buffer);
      if (lexer.getTokenType() == DOC_COMMENT) {
        lexer.advance();
        if (lexer.getTokenType() == null) {
          return true;
        }
      }
      return false;
    }
  };

  TokenSet ALL_JAVADOC_ELEMENTS = TokenSet.create(
    DOC_TAG, DOC_INLINE_TAG, DOC_METHOD_OR_FIELD_REF, DOC_PARAMETER_REF, DOC_TAG_VALUE_ELEMENT,
    DOC_REFERENCE_HOLDER, DOC_TYPE_HOLDER, DOC_COMMENT);
}
