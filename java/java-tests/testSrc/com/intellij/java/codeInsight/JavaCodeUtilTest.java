// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.PsiExpressionStatementImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;

public class JavaCodeUtilTest extends LightJavaCodeInsightTestCase {
  public void testReplace() {
    PsiFileFactory instance = PsiFileFactory.getInstance(getProject());
    PsiJavaFile aFile = (PsiJavaFile)instance
      .createFileFromText("a.java", JavaFileType.INSTANCE, """
        class Foo {
            void foo(){
            final int i = 0;    }
        }""");
    PsiClass aClass = aFile.getClasses()[0];
    PsiDeclarationStatement firstStatement = (PsiDeclarationStatement)aClass.getMethods()[0].getBody().getStatements()[0];
    PsiLocalVariable variable1 = (PsiLocalVariable)firstStatement.getDeclaredElements()[0];

    PsiJavaFile aFile2 = (PsiJavaFile)instance
      .createFileFromText("a.java", JavaFileType.INSTANCE, """
        class Foo {
            void foo(){
            int i = 0;    }
        }""");
    PsiClass aClass2 = aFile2.getClasses()[0];
    PsiDeclarationStatement firstStatement2 = (PsiDeclarationStatement)aClass2.getMethods()[0].getBody().getStatements()[0];
    PsiLocalVariable variable2 = (PsiLocalVariable)firstStatement2.getDeclaredElements()[0];

    variable1.getModifierList().replace(variable2.getModifierList());

    assertEquals("int i = 0;", variable1.getText());
  }

  public void testReplaceDoesNotTruncateTrailingWhitespace() {
    configureFromFileText("a.java",
                          "class Foo {\n" +
                          "    void foo(){\n" +
                          "        ArrayList \n" + //note whitespace after
                          "    }\n" +
                          "}");
    PsiClass aClass = ((PsiJavaFile)getFile()).getClasses()[0];
    PsiCodeBlock body = aClass.getMethods()[0].getBody();
    PsiReferenceExpression expression = (PsiReferenceExpression)((PsiExpressionStatementImpl)body.getStatements()[0]).getExpression();

    PsiClass list = getJavaFacade().findClass("java.util.ArrayList", GlobalSearchScope.allScope(getProject()));

    ApplicationManager.getApplication().runWriteAction(() -> {
      PsiElement newElement = expression.bindToElement(list);
      CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(newElement);
    });

    assertEquals("""
                   import java.util.ArrayList;

                   class Foo {
                       void foo(){
                           ArrayList\s
                       }
                   }""", getFile().getText());
  }
}
