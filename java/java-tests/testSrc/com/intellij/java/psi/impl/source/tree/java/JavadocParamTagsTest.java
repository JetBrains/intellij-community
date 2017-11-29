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
package com.intellij.java.psi.impl.source.tree.java;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.testFramework.LightIdeaTestCase;
import com.intellij.util.IncorrectOperationException;

/**
 *  @author dsl
 */
public class JavadocParamTagsTest extends LightIdeaTestCase {
  public void testDeleteTag1() {
    final PsiElementFactory factory = getFactory();
    final PsiMethod method = factory.createMethodFromText(
          "/**\n" +
          " * Javadoc\n" +
          " * @param p1\n" +
          " * @param p2\n" +
          " */" +
          "  void m() {}", null);
    final PsiDocComment docComment = method.getDocComment();
    assertNotNull(docComment);
    final PsiDocTag[] tags = docComment.getTags();
    WriteCommandAction.runWriteCommandAction(null, () -> tags[0].delete());

    assertEquals("/**\n" +
                 " * Javadoc\n" +
                 " * @param p2\n" +
                 " */", docComment.getText());

  }

  public void testDeleteTag2() {
    final PsiElementFactory factory = getFactory();
    final PsiMethod method = factory.createMethodFromText(
          "/**\n" +
          " * Javadoc\n" +
          " * @param p1\n" +
          " * @param p2\n" +
          " */" +
          "  void m() {}", null);
    final PsiDocComment docComment = method.getDocComment();
    assertNotNull(docComment);
    final PsiDocTag[] tags = docComment.getTags();
    ApplicationManager.getApplication().runWriteAction(() -> tags[1].delete());

    assertEquals("/**\n" +
                 " * Javadoc\n" +
                 " * @param p1\n" +
                 " */", docComment.getText());

  }

  public void testDeleteTag3() {
    final PsiElementFactory factory = getFactory();
    final PsiMethod method = factory.createMethodFromText(
          "/**\n" +
          " * Javadoc\n" +
          " * @param p1\n" +
          " * @param p2\n" +
          " * @param p3\n" +
          " */" +
          "  void m() {}", null);
    final PsiDocComment docComment = method.getDocComment();
    assertNotNull(docComment);
    final PsiDocTag[] tags = docComment.getTags();
    ApplicationManager.getApplication().runWriteAction(() -> tags[1].delete());

    assertEquals("/**\n" +
                 " * Javadoc\n" +
                 " * @param p1\n" +
                 " * @param p3\n" +
                 " */", docComment.getText());
  }

  public void testTagCreation() {
    createAndTestTag("@param p1 Text", "p1", "Text");
    createAndTestTag("@param p2", "p2", "");
    createAndTestTag("@param p2 FirstLine\n * SecondLine", "p2", "FirstLine\nSecondLine");
  }

  public void testAddTag1() {
    final PsiElementFactory factory = getFactory();
    final PsiMethod method = factory.createMethodFromText(
          "/**\n" +
          " * Javadoc\n" +
          " * @param p1\n" +
          " */\n" +
          "void m();", null);
    final PsiDocComment docComment = method.getDocComment();
    assertNotNull(docComment);
    final PsiDocTag[] tags = docComment.getTags();
    final PsiDocTag tag2 = factory.createParamTag("p2", "");
    docComment.addAfter(tag2, tags[0]);
    assertEquals(
      "/**\n" +
      " * Javadoc\n" +
      " * @param p1\n" +
      " * @param p2\n" +
      " */", docComment.getText());
  }

  public void testAddTag2() {
    final PsiElementFactory factory = getFactory();
    final PsiMethod method = factory.createMethodFromText(
          "/**\n" +
          " * Javadoc\n" +
          " * @param p1\n" +
          " */\n" +
          "void m();", null);
    final PsiDocComment docComment = method.getDocComment();
    assertNotNull(docComment);
    final PsiDocTag[] tags = docComment.getTags();
    final PsiDocTag tag2 = factory.createParamTag("p2", "");
    docComment.addBefore(tag2, tags[0]);
    assertEquals(
      "/**\n" +
      " * Javadoc\n" +
      " * @param p2\n" +
      " * @param p1\n" +
      " */", docComment.getText());
  }

  public void testAddTag3() {
    CommandProcessor.getInstance().executeCommand(getProject(), () -> ApplicationManager.getApplication().runWriteAction(() -> {
      final PsiElementFactory factory = getFactory();
      final PsiJavaFile psiFile;
      try {
        psiFile = (PsiJavaFile)createFile("aaa.java", "class A {/**\n" +
                                                      " * Javadoc\n" +
                                                      " * @param p1\n" +
                                                      " * @param p3\n" +
                                                      " */\n" +
                                                      "void m();}");
      final PsiClass psiClass = psiFile.getClasses()[0];
      final PsiMethod method = psiClass.getMethods()[0];
      PsiDocComment docComment = method.getDocComment();
      assertNotNull(docComment);
      final PsiDocTag[] tags = docComment.getTags();
      final PsiDocTag tag2 = factory.createParamTag("p2", "");
      docComment.addAfter(tag2, tags[0]);
      docComment = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(docComment);
      assertEquals(
        "/**\n" +
        " * Javadoc\n" +
        " * @param p1\n" +
        " * @param p2\n" +
        " * @param p3\n" +
        " */", docComment.getText());
      }
      catch (IncorrectOperationException e) {}
    }), "", null);
  }

  public void testAddTag4() {
    final PsiElementFactory factory = getFactory();
    final PsiMethod method = factory.createMethodFromText(
          "/**\n" +
          " * Javadoc\n" +
          " */\n" +
          "void m();", null);
    final PsiDocComment docComment = method.getDocComment();
    assertNotNull(docComment);
    final PsiDocTag tag2 = factory.createParamTag("p2", "");
    docComment.add(tag2);
    assertEquals(
      "/**\n" +
      " * Javadoc\n" +
      " * @param p2\n" +
      " */", docComment.getText());
  }

  private static PsiElementFactory getFactory() {
    final PsiManager manager = getPsiManager();
    return JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
  }

  private static void createAndTestTag(String expectedText, String parameterName, String description) throws IncorrectOperationException {
    PsiElementFactory factory = getFactory();
    final PsiDocTag paramTag = factory.createParamTag(parameterName, description);
    assertEquals(expectedText, paramTag.getText());
  }
}
