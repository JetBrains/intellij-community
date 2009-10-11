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
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.parsing.JavaParsingContext;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ILazyParseableElementType;
import com.intellij.psi.tree.IReparseableElementType;
import com.intellij.psi.tree.java.IJavaDocElementType;
import com.intellij.util.CharTable;

public interface JavaDocElementType {
  //chameleon
  IElementType DOC_TAG = new IJavaDocElementType("DOC_TAG");
  IElementType DOC_TAG_VALUE = new IJavaDocElementType("DOC_TAG_VALUE");
  IElementType DOC_INLINE_TAG = new IJavaDocElementType("DOC_INLINE_TAG");
  IElementType DOC_METHOD_OR_FIELD_REF = new IJavaDocElementType("DOC_METHOD_OR_FIELD_REF");
  IElementType DOC_PARAMETER_REF = new IJavaDocElementType("DOC_PARAMETER_REF");

  ILazyParseableElementType DOC_REFERENCE_HOLDER = new ILazyParseableElementType("DOC_REFERENCE_HOLDER", StdLanguages.JAVA){
    public ASTNode parseContents(ASTNode chameleon) {
      final CharSequence chars = chameleon.getChars();
      final PsiManager manager = chameleon.getTreeParent().getPsi().getManager();
      final CharTable table = SharedImplUtil.findCharTableByTree(chameleon);
      //no language features from higher java language versions are present in javadoc
      JavaParsingContext context = new JavaParsingContext(table, LanguageLevel.JDK_1_3);
      return context.getJavadocParsing().parseJavaDocReference(chars, new JavaLexer(LanguageLevel.JDK_1_3), false, manager);
    }

    @Override
    public ASTNode createNode(CharSequence text) {
      return new LazyParseablePsiElement(this, text);
    }
  };

  ILazyParseableElementType DOC_TYPE_HOLDER = new ILazyParseableElementType("DOC_TYPE_HOLDER", StdLanguages.JAVA){
    public ASTNode parseContents(ASTNode chameleon) {
      final CharSequence chars = chameleon.getChars();
      final PsiManager manager = chameleon.getTreeParent().getPsi().getManager();
      final CharTable table = SharedImplUtil.findCharTableByTree(chameleon);
      //no language features from higher java language versions are present in javadoc
      JavaParsingContext context = new JavaParsingContext(table, LanguageLevel.JDK_1_3);
      return context.getJavadocParsing().parseJavaDocReference(chars, new JavaLexer(LanguageLevel.JDK_1_3), true, manager);
    }

    @Override
    public ASTNode createNode(CharSequence text) {
      return new LazyParseablePsiElement(this, text);
    }
  };

  ILazyParseableElementType DOC_COMMENT = new IReparseableElementType("DOC_COMMENT", StdLanguages.JAVA){
    public ASTNode parseContents(ASTNode chameleon) {
      final CharSequence chars = chameleon.getChars();
      final PsiManager manager = chameleon.getTreeParent().getPsi().getManager();
      //no higher java language level features are allowed in javadoc
      final JavaParsingContext context = new JavaParsingContext(SharedImplUtil.findCharTableByTree(chameleon), LanguageLevel.JDK_1_3);
      return context.getJavadocParsing().parseDocCommentText(manager, chars, 0, chars.length());
    }

    public boolean isParsable(CharSequence buffer, final Project project) {
      final JavaLexer lexer = new JavaLexer(LanguageLevel.JDK_1_5);

      lexer.start(buffer);
      if(lexer.getTokenType() != DOC_COMMENT) return false;
      lexer.advance();
      if(lexer.getTokenType() != null) return false;
      return true;
    }
  };
}
