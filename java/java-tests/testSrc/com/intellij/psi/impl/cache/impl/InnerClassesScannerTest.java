/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi.impl.cache.impl;

import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.impl.source.PsiJavaFileImpl;
import com.intellij.psi.impl.source.tree.StdTokenSets;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.FilterLexer;

import java.io.File;
import java.util.List;
import java.util.ArrayList;

/**
 * @author dsl
 */
public class InnerClassesScannerTest extends PsiTestCase {
  public void testClassWithGenericParameters() throws Exception { doTest(); }

  public void testNewInsideNew() throws Exception { doTest(); }

  private void doTest() throws Exception {
    configureByFileWithMarker(
      PathManagerEx.getTestDataPath() + "/psi/repositoryUse/innerClassesScanner/".replace('/', File.separatorChar) + getTestName(false) + ".java",
      "");
    final List<PsiClass> list = getInnerClasses(((PsiJavaFile)myFile).getClasses()[0].getMethods()[0], myFile.getViewProvider().getContents());
    assertTrue(list != null && list.size() == 1);
  }

  private static List<PsiClass> getInnerClasses(PsiElement psiElement, final CharSequence fileBuffer) {
    final Ref<ArrayList<PsiClass>> ourList = new Ref<>();
    ourList.set(null);

    if (psiElement != null && mayContainClassesInside(psiElement, fileBuffer)) {
      psiElement.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override public void visitClass(PsiClass aClass) {
          if (ourList.isNull()) ourList.set(new ArrayList<>());
          ourList.get().add(aClass);
        }

        @Override public void visitTypeParameter(PsiTypeParameter classParameter) {
          // just skip (because type parameter is class - bad!)
        }
      });
    }

    return ourList.get();
  }

  private static boolean mayContainClassesInside(PsiElement psiElement, final CharSequence fileBuffer) {
    PsiFile psiFile = psiElement.getContainingFile();

    boolean mayHaveClassesInside = false;
    if (psiFile instanceof PsiJavaFileImpl) {
      Lexer originalLexer = JavaParserDefinition.createLexer(((PsiJavaFileImpl)psiFile).getLanguageLevel());
      FilterLexer lexer = new FilterLexer(originalLexer, new FilterLexer.SetFilter(StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET));
      final TextRange range = psiElement.getTextRange();
      lexer.start(fileBuffer, range.getStartOffset(), range.getEndOffset());
      boolean isInNewExpression = false;
      boolean isRightAfterNewExpression = false;
      int angleLevel = 0;
      int parenLevel = 0;
      do {
        IElementType tokenType = lexer.getTokenType();
        if (tokenType == null) break;

        if (tokenType == JavaTokenType.NEW_KEYWORD) {
          isInNewExpression = true;
        }
        else if (tokenType == JavaTokenType.LPARENTH) {
          if (isInNewExpression) parenLevel++;
        }
        else if (tokenType == JavaTokenType.LT) {
          if (isInNewExpression) angleLevel++;
        }
        else if (tokenType == JavaTokenType.GT) {
          if (isInNewExpression) angleLevel--;
        }
        else if (tokenType == JavaTokenType.RPARENTH) {
          if (isInNewExpression) {
            parenLevel--;
            if (parenLevel == 0) {
              isRightAfterNewExpression = true;
            }
          }
        }
        else if (tokenType == JavaTokenType.LBRACE) {
          if (isInNewExpression || isRightAfterNewExpression) {
            mayHaveClassesInside = true;
          }
        }
        else if (tokenType == JavaTokenType.LBRACKET) {
          if (parenLevel == 0 && angleLevel == 0) isInNewExpression = false;
        }
        else if (tokenType == JavaTokenType.INTERFACE_KEYWORD || tokenType == JavaTokenType.CLASS_KEYWORD ||
                 tokenType == JavaTokenType.ENUM_KEYWORD) {
          mayHaveClassesInside = true;
        }

        if (isInNewExpression && isRightAfterNewExpression) {
          isInNewExpression = false;
        }
        else {
          isRightAfterNewExpression = false;
        }

        lexer.advance();
      }
      while (!mayHaveClassesInside);
    }
    return mayHaveClassesInside;
  }
}
