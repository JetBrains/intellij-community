// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.navigation;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.icons.AllIcons;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class RunLineMarkerJava23Test extends RunLineMarkerJava22Test {

  @Override
  protected @NotNull LanguageLevel getEnabledLevel() {
    return LanguageLevel.JDK_23_PREVIEW;
  }

  @Override
  protected @NotNull LanguageLevel getDisabledLevel() {
    return LanguageLevel.JDK_23;
  }

  public void testStaticMainMethodInSuperClass() {
    myFixture.addClass("public class B { public static void main(String[] args) {} }");
    IdeaTestUtil.withLevel(getModule(), getDisabledLevel(), () -> {
      myFixture.configureByText("MainTest.java", """
        class A extends B {}
        """);
      List<GutterMark> marks = myFixture.findAllGutters();
      assertEquals(1, marks.size());
    });
  }

  public void testBasic() {
    IdeaTestUtil.withLevel(getModule(),  getDisabledLevel(), () -> {
      myFixture.configureByText("MainTest.java", """
        class A{
          public static void main<caret>(String[] args) {
          }
        }
        """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(1, marks.size());
      GutterMark mark = marks.get(0);
      assertTrue(mark instanceof LineMarkerInfo.LineMarkerGutterIconRenderer);
      LineMarkerInfo.LineMarkerGutterIconRenderer gutterIconRenderer = (LineMarkerInfo.LineMarkerGutterIconRenderer)mark;
      PsiElement element = gutterIconRenderer.getLineMarkerInfo().getElement();
      assertEquals(AllIcons.RunConfigurations.TestState.Run, gutterIconRenderer.getIcon());
      assertTrue(element instanceof PsiIdentifier);
      assertEquals("main", element.getText());
    });
  }
}
