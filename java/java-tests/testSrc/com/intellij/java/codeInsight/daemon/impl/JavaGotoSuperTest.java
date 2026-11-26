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

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.daemon.GutterIconDescriptor;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerSettings;
import com.intellij.codeInsight.daemon.impl.JavaLineMarkerProvider;
import com.intellij.codeInsight.daemon.impl.LineMarkersPass;
import com.intellij.codeInsight.daemon.impl.MarkerType;
import com.intellij.ide.DataManager;
import com.intellij.lang.CodeInsightActions;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.NavigationTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.awt.event.MouseEvent;
import java.util.List;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class JavaGotoSuperTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture);

  private static String getBasePath() {
    return "/codeInsight/gotosuper/";
  }

  @Test
  public void testLambda() {
    doTest();
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    daemonFixture.get().configureByFile(getBasePath() + myFixture.getTestName(false) + ".java");
    final CodeInsightActionHandler handler = CodeInsightActions.GOTO_SUPER.forLanguage(JavaLanguage.INSTANCE);
    EdtTestUtil.runInEdtAndWait(() -> {
      handler.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
      NavigationTestUtil.awaitPendingNavigation(myFixture.getProject());
    });
    myFixture.checkResultByFile(getBasePath() + myFixture.getTestName(false) + ".after.java");
  }

  @Test
  public void testLambdaMarker() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    GutterIconDescriptor.Option option = JavaLineMarkerProvider.LAMBDA_OPTION;
    boolean isEnabled = LineMarkerSettings.getSettings().isEnabled(option);
    LineMarkerSettings.getSettings().setEnabled(option, true);
    Disposer.register(myFixture.getTestRootDisposable(), () -> LineMarkerSettings.getSettings().setEnabled(option, isEnabled));

    daemonFixture.get().configureByFile(getBasePath() + myFixture.getTestName(false) + ".java");

    daemonFixture.get().doHighlighting();
    boolean noGutter = EdtTestUtil.runInEdtAndGet(() -> CodeInsightTestFixtureImpl.processGuttersAtCaret(myFixture.getEditor(), myFixture.getProject(), mark -> {
      Shortcut shortcut = ActionManager.getInstance().getAction(IdeActions.ACTION_GOTO_SUPER).getShortcutSet().getShortcuts()[0];
      assertEquals(
        "<html><body><p>Overrides method in <code><a href=\"#element/I#run\"><span style=\"color:#000000;\">I</span></a></code></p><p style='margin-top:8px;'><font color='#787878'>Press " +
        KeymapUtil.getShortcutText(shortcut) +
        " to navigate</font></p></body></html>",
        mark.getTooltipText());
      return false;
    }));
    if (noGutter) {
      fail("Gutter expected");
    }
  }

  @Test
  public void testSiblingInheritance() {
    doTest();
  }

  @Test
  public void testSiblingInheritanceLineMarkers() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    daemonFixture.get().configureByFile(getBasePath() + "SiblingInheritance.java");
    PsiMethod[] methods = ReadAction.computeBlocking(() -> {
      PsiJavaFile file = (PsiJavaFile)myFixture.getFile();
      PsiClass i = JavaPsiFacade.getInstance(myFixture.getProject()).findClass("z.I", GlobalSearchScope.fileScope(file));
      PsiClass a = JavaPsiFacade.getInstance(myFixture.getProject()).findClass("z.A", GlobalSearchScope.fileScope(file));
      return new PsiMethod[]{i.getMethods()[0], a.getMethods()[0]};
    });
    PsiMethod iRun = methods[0];
    assertEquals("run", ReadAction.computeBlocking(() -> iRun.getName()));
    PsiMethod aRun = methods[1];
    assertEquals("run", ReadAction.computeBlocking(() -> aRun.getName()));
    daemonFixture.get().doHighlighting();
    Document document = myFixture.getEditor().getDocument();
    ReadAction.runBlocking(() -> {
      List<LineMarkerInfo<?>> markers = LineMarkersPass.getDisplayedLineMarkers(document, myFixture.getProject());
      assertTrue(markers.size() >= 2);
      LineMarkerInfo<?> iMarker = findMarkerWithElement(markers, iRun.getNameIdentifier());
      assertSame(MarkerType.OVERRIDDEN_METHOD.getNavigationHandler(), iMarker.getNavigationHandler());

      LineMarkerInfo<?> aMarker = findMarkerWithElement(markers, aRun.getNameIdentifier());
      assertSame(MarkerType.SIBLING_OVERRIDING_METHOD.getNavigationHandler(), aMarker.getNavigationHandler());
    });
  }

  @Test
  public void testSiblingInheritanceGutterNavigatesToSuper() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    daemonFixture.get().configureByFile(getBasePath() + "SiblingInheritance.java");
    PsiMethod[] methods = ReadAction.computeBlocking(() -> {
      PsiJavaFile file = (PsiJavaFile)myFixture.getFile();
      PsiClass i = JavaPsiFacade.getInstance(myFixture.getProject()).findClass("z.I", GlobalSearchScope.fileScope(file));
      PsiClass a = JavaPsiFacade.getInstance(myFixture.getProject()).findClass("z.A", GlobalSearchScope.fileScope(file));
      return new PsiMethod[]{i.getMethods()[0], a.getMethods()[0]};
    });
    PsiMethod iRun = methods[0];
    PsiMethod aRun = methods[1];

    daemonFixture.get().doHighlighting();
    @NotNull Document document = myFixture.getEditor().getDocument();
    EdtTestUtil.runInEdtAndWait(() -> {
      List<LineMarkerInfo<?>> markers =
        LineMarkersPass.getDisplayedLineMarkers(document, myFixture.getProject());
      LineMarkerInfo<?> aMarker = findMarkerWithElement(markers, aRun.getNameIdentifier());
      assertSame(MarkerType.SIBLING_OVERRIDING_METHOD.getNavigationHandler(), aMarker.getNavigationHandler());

      @SuppressWarnings("unchecked")
      GutterIconNavigationHandler<PsiElement> handler =
        (GutterIconNavigationHandler<PsiElement>)aMarker.getNavigationHandler();
      handler.navigate(fakeClickEvent(), aMarker.getElement());
      NavigationTestUtil.awaitPendingNavigation(myFixture.getProject());

      int caretOffset = myFixture.getEditor().getCaretModel().getOffset();
      assertTrue(
        iRun.getNameIdentifier().getTextRange().contains(caretOffset),
        "Gutter click on A#run must navigate to I#run, but caret is at offset " + caretOffset);
    });
  }

  @Test
  public void testSiblingInheritanceGutterNavigatesToSealedSuper() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    daemonFixture.get().configureByFile(getBasePath() + "SiblingInheritanceSealed.java");
    PsiMethod[] methods = ReadAction.computeBlocking(() -> {
      PsiJavaFile file = (PsiJavaFile)myFixture.getFile();
      PsiClass derivative = JavaPsiFacade.getInstance(myFixture.getProject())
        .findClass("z.Derivative", GlobalSearchScope.fileScope(file));
      PsiClass abstractDerivative = JavaPsiFacade.getInstance(myFixture.getProject())
        .findClass("z.AbstractDerivative", GlobalSearchScope.fileScope(file));
      return new PsiMethod[]{derivative.getMethods()[0], abstractDerivative.getMethods()[0]};
    });
    PsiMethod superGet = methods[0];
    PsiMethod absGet = methods[1];

    daemonFixture.get().doHighlighting();
    @NotNull Document document = myFixture.getEditor().getDocument();
    EdtTestUtil.runInEdtAndWait(() -> {
      List<LineMarkerInfo<?>> markers =
        LineMarkersPass.getDisplayedLineMarkers(document, myFixture.getProject());
      LineMarkerInfo<?> marker = findMarkerWithElement(markers, absGet.getNameIdentifier());
      assertSame(MarkerType.SIBLING_OVERRIDING_METHOD.getNavigationHandler(), marker.getNavigationHandler());

      @SuppressWarnings("unchecked")
      GutterIconNavigationHandler<PsiElement> handler =
        (GutterIconNavigationHandler<PsiElement>)marker.getNavigationHandler();
      handler.navigate(fakeClickEvent(), marker.getElement());
      NavigationTestUtil.awaitPendingNavigation(myFixture.getProject());

      int caretOffset = myFixture.getEditor().getCaretModel().getOffset();
      assertTrue(
        superGet.getNameIdentifier().getTextRange().contains(caretOffset),
        "Gutter click on AbstractDerivative#getExpiration must navigate to Derivative#getExpiration, caret at " + caretOffset);
    });
  }

  private MouseEvent fakeClickEvent() {
    return new MouseEvent(daemonFixture.get().getFixture().getEditor().getContentComponent(), MouseEvent.MOUSE_CLICKED,
                          System.currentTimeMillis(), 0, 0, 0, 1, false);
  }

  @Test
  public void testSiblingInheritanceLineMarkersEvenIfMethodIsFinal() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    daemonFixture.get().configureByFile(getBasePath() + "SiblingInheritanceFinal.java");
    PsiMethod[] methods = ReadAction.computeBlocking(() -> {
      PsiJavaFile file = (PsiJavaFile)myFixture.getFile();
      PsiClass i = JavaPsiFacade.getInstance(myFixture.getProject()).findClass("z.I", GlobalSearchScope.fileScope(file));
      PsiClass a = JavaPsiFacade.getInstance(myFixture.getProject()).findClass("z.A", GlobalSearchScope.fileScope(file));
      return new PsiMethod[]{i.getMethods()[0], a.getMethods()[0]};
    });
    PsiMethod iRun = methods[0];
    assertEquals("run", ReadAction.computeBlocking(() -> iRun.getName()));
    PsiMethod aRun = methods[1];
    assertEquals("run", ReadAction.computeBlocking(() -> aRun.getName()));
    daemonFixture.get().doHighlighting();
    Document document = myFixture.getEditor().getDocument();
    ReadAction.runBlocking(() -> {
      List<LineMarkerInfo<?>> markers = LineMarkersPass.getDisplayedLineMarkers(document, myFixture.getProject());
      assertTrue(markers.size() >= 2);
      LineMarkerInfo<?> iMarker = findMarkerWithElement(markers, iRun.getNameIdentifier());
      assertSame(MarkerType.OVERRIDDEN_METHOD.getNavigationHandler(), iMarker.getNavigationHandler());

      LineMarkerInfo<?> aMarker = findMarkerWithElement(markers, aRun.getNameIdentifier());
      assertSame(MarkerType.SIBLING_OVERRIDING_METHOD.getNavigationHandler(), aMarker.getNavigationHandler());
    });
  }

  private static LineMarkerInfo<?> findMarkerWithElement(List<LineMarkerInfo<?>> markers, PsiElement psiMethod) {
    LineMarkerInfo<?> marker = ContainerUtil.find(markers, info -> info.getElement().equals(psiMethod));
    assertNotNull(marker, markers.toString());
    return marker;
  }

  @Test
  public void testSiblingInheritanceGoDown() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    daemonFixture.get().configureByFile(getBasePath() + "SiblingInheritance.after.java");
    EdtTestUtil.runInEdtAndWait(() -> {
      AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_GOTO_IMPLEMENTATION);
      AnActionEvent event = AnActionEvent.createFromAnAction(action, null, "", DataManager.getInstance().getDataContextFromFocus().getResultSync());
      action.update(event);
      assertTrue(event.getPresentation().isEnabledAndVisible());
      action.actionPerformed(event);
      NavigationTestUtil.awaitPendingNavigation(myFixture.getProject());
    });
    myFixture.checkResultByFile(getBasePath() + "SiblingInheritance.java");
  }

  @Test
  public void testGoToImplementations() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    daemonFixture.get().configureByFile(getBasePath() + "GoToImplementations.java");
    EdtTestUtil.runInEdtAndWait(() -> {
      AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_GOTO_IMPLEMENTATION);
      AnActionEvent event = AnActionEvent.createFromAnAction(action, null, "", DataManager.getInstance().getDataContextFromFocus().getResultSync());
      action.update(event);
      assertTrue(event.getPresentation().isEnabledAndVisible());
      action.actionPerformed(event);
      NavigationTestUtil.awaitPendingNavigation(myFixture.getProject());
    });
    myFixture.checkResultByFile(getBasePath() + "GoToImplementations.after.java");
  }

  @Test
  public void testSiblingInheritanceAndGenerics() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    daemonFixture.get().configureByFile(getBasePath() + "SiblingInheritanceAndGenerics.java");
    EdtTestUtil.runInEdtAndWait(() -> {
      AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_GOTO_SUPER);
      AnActionEvent event = AnActionEvent.createFromAnAction(action, null, "", DataManager.getInstance().getDataContextFromFocus().getResultSync());
      action.update(event);
      assertTrue(event.getPresentation().isEnabledAndVisible());
      action.actionPerformed(event);
      NavigationTestUtil.awaitPendingNavigation(myFixture.getProject());
    });
    myFixture.checkResultByFile(getBasePath() + "SiblingInheritanceAndGenerics.after.java");
  }

  @Test
  public void testDoNotShowSiblingInheritanceLineMarkerIfSubclassImplementsTheSameInterfaceAsTheCurrentClass() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    daemonFixture.get().configureByFile(getBasePath() + "DeceivingSiblingInheritance.java");
    PsiClass OCBaseLanguageFileType = ReadAction.computeBlocking(() -> {
      PsiJavaFile file = (PsiJavaFile)myFixture.getFile();
      return JavaPsiFacade.getInstance(myFixture.getProject()).findClass("z.OCBaseLanguageFileType", GlobalSearchScope.fileScope(file));
    });
    PsiMethod getName = ReadAction.computeBlocking(() -> OCBaseLanguageFileType.getMethods()[0]);
    assertEquals("getName", ReadAction.computeBlocking(() -> getName.getName()));

    daemonFixture.get().doHighlighting();
    Document document = myFixture.getEditor().getDocument();
    ReadAction.runBlocking(() -> {
      List<LineMarkerInfo<?>> markers = LineMarkersPass.getDisplayedLineMarkers(document, myFixture.getProject());
      List<LineMarkerInfo<?>> inMyClass = ContainerUtil.filter(markers, info -> OCBaseLanguageFileType.getTextRange().containsRange(info.startOffset, info.endOffset));
      assertEquals(2, inMyClass.size(), inMyClass.toString());
      LineMarkerInfo<?> iMarker = findMarkerWithElement(inMyClass, getName.getNameIdentifier());
      assertSame(MarkerType.OVERRIDING_METHOD.getNavigationHandler(), iMarker.getNavigationHandler());

      LineMarkerInfo<?> aMarker = findMarkerWithElement(inMyClass, OCBaseLanguageFileType.getNameIdentifier());
      assertSame(MarkerType.SUBCLASSED_CLASS.getNavigationHandler(), aMarker.getNavigationHandler());
    });
  }

}
