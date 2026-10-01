// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.impl.LineMarkersPass;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class RecursiveCallLineMarkerTest extends LightJavaCodeInsightFixtureTestCase {
  

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/recursive";
  }

  public void testQualifiedCall() {
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.doHighlighting();
    @NotNull Document document = getEditor().getDocument();
    final List<LineMarkerInfo<?>> infoList = LineMarkersPass.getDisplayedLineMarkers(document, getProject());
   
    assertSize(2, infoList);
    for (LineMarkerInfo<?> info : infoList) {
      assertEquals(JavaBundle.message("tooltip.recursive.call"), info.getLineMarkerTooltip());
    }
  }
}