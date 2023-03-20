// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewPopupUpdateProcessor;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.lang.JavaVersion;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Stream;

public class IncreaseLanguageLevelFixTest extends LightDaemonAnalyzerTestCase {
  @Override
  protected @NotNull String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/increaseLanguageLevel/";
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk(JavaVersion.compose(17));
  }

  @Override
  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_1_8;
  }

  public void testVarLocal() {
    doTest(LanguageLevel.JDK_10);
  }

  public void testVarLambda() {
    doTest(LanguageLevel.JDK_11);
  }

  public void testRecordTopLevel() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_15, () -> doTest(LanguageLevel.JDK_16));
  }

  public void testRecordInClass() {
    doTest(LanguageLevel.JDK_16);
  }

  public void testRecordInMethod() {
    doTest(LanguageLevel.JDK_16);
  }

  public void testSealedClasses() {
    doTest(LanguageLevel.JDK_17);
  }

  private void doTest(LanguageLevel level) {
    configureByFile(getTestName(false) + ".java");
    doHighlighting();
    List<IntentionAction> actions = CodeInsightTestFixtureImpl.getAvailableIntentions(getEditor(), getFile());
    String message = JavaBundle.message("set.language.level.to.0", level.getPresentableText());
    IntentionAction foundAction = ContainerUtil.find(actions, act -> act.getText().equals(message));
    if (foundAction == null) {
      LanguageLevel foundLevel = Stream.of(LanguageLevel.values())
        .filter(l -> ContainerUtil.exists(actions, act -> act.getText()
          .equals(JavaBundle.message("set.language.level.to.0", l.getPresentableText()))))
        .findFirst().orElse(null);
      if (foundLevel != null) {
        fail("Expected level: "+level+"; actual: "+foundLevel);
      } else {
        fail("Action " + message + " not found");
      }
    }
    else {
      String actualPreview = IntentionPreviewPopupUpdateProcessor.getPreviewContent(getProject(), foundAction, getFile(), getEditor());
      JavaVersion javaVersion = level.toJavaVersion();
      String expectedPreview = JavaBundle.message("increase.language.level.preview.description", getModule().getName(), javaVersion);
      if (!expectedPreview.equals(actualPreview)) {
        failNotEquals(foundAction.getText(), expectedPreview, actualPreview);
      }
    }
  }
}
