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
package com.intellij.java.codeInsight.daemon.impl;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.MarkerType;
import com.intellij.ide.DataManager;
import com.intellij.lang.CodeInsightActions;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class JavaGotoSuperTest extends LightDaemonAnalyzerTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  private static String getBasePath() {
    return "/codeInsight/gotosuper/";
  }

  public void testLambda() {
    doTest();
  }

  private void doTest() {
    configureByFile(getBasePath() + getTestName(false) + ".java");
    final CodeInsightActionHandler handler = CodeInsightActions.GOTO_SUPER.forLanguage(JavaLanguage.INSTANCE);
    handler.invoke(getProject(), getEditor(), getFile());
    checkResultByFile(getBasePath() + getTestName(false) + ".after.java");
  }

  public void testLambdaMarker() {
    configureByFile(getBasePath() + getTestName(false) + ".java");

    doHighlighting();
    if (CodeInsightTestFixtureImpl.processGuttersAtCaret(getEditor(), getProject(), mark -> {
      Shortcut shortcut = ActionManager.getInstance().getAction(IdeActions.ACTION_GOTO_SUPER).getShortcutSet().getShortcuts()[0];
      assertEquals(
        "<html><body>Overrides method in <a href=\"#javaClass/I\">I</a><br><div style='margin-top: 5px'><font size='2'>Click or press " +
        KeymapUtil.getShortcutText(shortcut) +
        " to navigate</font></div></body></html>",
        mark.getTooltipText());
      return false;
    })) {
      fail("Gutter expected");
    }
  }

  public void testSiblingInheritance() {
    doTest();
  }

  public void testSiblingInheritanceLineMarkers() {
    configureByFile(getBasePath() + "SiblingInheritance.java");
    PsiJavaFile file = (PsiJavaFile)getFile();
    PsiClass i = JavaPsiFacade.getInstance(getProject()).findClass("z.I", GlobalSearchScope.fileScope(file));
    PsiClass a = JavaPsiFacade.getInstance(getProject()).findClass("z.A", GlobalSearchScope.fileScope(file));
    PsiMethod iRun = i.getMethods()[0];
    assertEquals("run", iRun.getName());
    PsiMethod aRun = a.getMethods()[0];
    assertEquals("run", aRun.getName());
    doHighlighting();
    Document document = getEditor().getDocument();
    List<LineMarkerInfo> markers = DaemonCodeAnalyzerImpl.getLineMarkers(document, getProject());
    assertTrue(markers.size() >= 2);
    LineMarkerInfo iMarker = findMarkerWithElement(markers, iRun.getNameIdentifier());
    assertSame(MarkerType.OVERRIDDEN_METHOD.getNavigationHandler(), iMarker.getNavigationHandler());

    LineMarkerInfo aMarker = findMarkerWithElement(markers, aRun.getNameIdentifier());
    assertSame(MarkerType.SIBLING_OVERRIDING_METHOD.getNavigationHandler(), aMarker.getNavigationHandler());
  }
  public void testSiblingInheritanceLineMarkersEvenIfMethodIsFinal() {
    configureByFile(getBasePath() + "SiblingInheritanceFinal.java");
    PsiJavaFile file = (PsiJavaFile)getFile();
    PsiClass i = JavaPsiFacade.getInstance(getProject()).findClass("z.I", GlobalSearchScope.fileScope(file));
    PsiClass a = JavaPsiFacade.getInstance(getProject()).findClass("z.A", GlobalSearchScope.fileScope(file));
    PsiMethod iRun = i.getMethods()[0];
    assertEquals("run", iRun.getName());
    PsiMethod aRun = a.getMethods()[0];
    assertEquals("run", aRun.getName());
    doHighlighting();
    Document document = getEditor().getDocument();
    List<LineMarkerInfo> markers = DaemonCodeAnalyzerImpl.getLineMarkers(document, getProject());
    assertTrue(markers.size() >= 2);
    LineMarkerInfo iMarker = findMarkerWithElement(markers, iRun.getNameIdentifier());
    assertSame(MarkerType.OVERRIDDEN_METHOD.getNavigationHandler(), iMarker.getNavigationHandler());

    LineMarkerInfo aMarker = findMarkerWithElement(markers, aRun.getNameIdentifier());
    assertSame(MarkerType.SIBLING_OVERRIDING_METHOD.getNavigationHandler(), aMarker.getNavigationHandler());
  }

  private static LineMarkerInfo findMarkerWithElement(List<LineMarkerInfo> markers, PsiElement psiMethod) {
    LineMarkerInfo marker = ContainerUtil.find(markers, info -> info.getElement().equals(psiMethod));
    assertNotNull(markers.toString(), marker);
    return marker;
  }

  public void testSiblingInheritanceGoDown() {
    configureByFile(getBasePath() + "SiblingInheritance.after.java");
    AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_GOTO_IMPLEMENTATION);
    AnActionEvent event = AnActionEvent.createFromAnAction(action, null, "", DataManager.getInstance().getDataContextFromFocus().getResultSync());
    action.update(event);
    assertTrue(event.getPresentation().isEnabledAndVisible());
    action.actionPerformed(event);
    checkResultByFile(getBasePath() + "SiblingInheritance.java");
  }

  public void testSiblingInheritanceAndGenerics() {
    configureByFile(getBasePath() + "SiblingInheritanceAndGenerics.java");
    AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_GOTO_SUPER);
    AnActionEvent event = AnActionEvent.createFromAnAction(action, null, "", DataManager.getInstance().getDataContextFromFocus().getResultSync());
    action.update(event);
    assertTrue(event.getPresentation().isEnabledAndVisible());
    action.actionPerformed(event);
    checkResultByFile(getBasePath() + "SiblingInheritanceAndGenerics.after.java");
  }

  public void testDoNotShowSiblingInheritanceLineMarkerIfSubclassImplementsTheSameInterfaceAsTheCurrentClass() {
    configureByFile(getBasePath() + "DeceivingSiblingInheritance.java");
    PsiJavaFile file = (PsiJavaFile)getFile();
    PsiClass OCBaseLanguageFileType = JavaPsiFacade.getInstance(getProject()).findClass("z.OCBaseLanguageFileType", GlobalSearchScope.fileScope(file));
    PsiMethod getName = OCBaseLanguageFileType.getMethods()[0];
    assertEquals("getName", getName.getName());

    doHighlighting();
    Document document = getEditor().getDocument();
    List<LineMarkerInfo> markers = DaemonCodeAnalyzerImpl.getLineMarkers(document, getProject());
    List<LineMarkerInfo> inMyClass = ContainerUtil.filter(markers, info -> OCBaseLanguageFileType.getTextRange().containsRange(info.startOffset, info.endOffset));
    assertTrue(inMyClass.toString(), inMyClass.size() == 2);
    LineMarkerInfo iMarker = findMarkerWithElement(inMyClass, getName.getNameIdentifier());
    assertSame(MarkerType.OVERRIDING_METHOD.getNavigationHandler(), iMarker.getNavigationHandler());

    LineMarkerInfo aMarker = findMarkerWithElement(inMyClass, OCBaseLanguageFileType.getNameIdentifier());
    assertSame(MarkerType.SUBCLASSED_CLASS.getNavigationHandler(), aMarker.getNavigationHandler());
  }

}
