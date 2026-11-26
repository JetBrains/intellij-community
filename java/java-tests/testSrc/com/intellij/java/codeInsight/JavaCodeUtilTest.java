// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.impl.source.tree.java.PsiExpressionStatementImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class JavaCodeUtilTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData");

  @Test
  public void testReplace() {
    ReadAction.runBlocking(() -> {
      PsiFileFactory instance = PsiFileFactory.getInstance(codeInsightFixture.get().getProject());
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
    });
  }

  @Test
  public void testReplaceDoesNotTruncateTrailingWhitespace() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java",
                              "class Foo {\n" +
                              "    void foo(){\n" +
                              "        ArrayList \n" + //note whitespace after
                              "    }\n" +
                              "}");
    myFixture.runInCommand(() -> {
      PsiClass aClass = ((PsiJavaFile)myFixture.getFile()).getClasses()[0];
      PsiCodeBlock body = aClass.getMethods()[0].getBody();
      PsiReferenceExpression expression = (PsiReferenceExpression)((PsiExpressionStatementImpl)body.getStatements()[0]).getExpression();

      PsiClass list = JavaPsiFacade.getInstance(myFixture.getProject()).findClass("java.util.ArrayList", GlobalSearchScope.allScope(myFixture.getProject()));

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
                     }""", myFixture.getFile().getText());
    });
  }
}
