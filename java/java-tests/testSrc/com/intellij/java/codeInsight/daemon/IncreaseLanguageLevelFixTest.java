// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerOptions;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewPopupUpdateProcessor;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.lang.JavaVersion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public abstract class IncreaseLanguageLevelFixTest {
  @TestApplication
  public static class IncreaseLanguageLevel25FixTest extends IncreaseLanguageLevelFixTest {
    private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(descriptor(25));
    private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = daemonFixture(projectFixture);

    @Override
    protected LightDaemonAnalyzerFixture daemon() {
      return daemonFixture.get();
    }

    @Test
    public void testStatementBeforeSuper() {
      IdeaTestUtil.withLevel(daemon().getFixture().getModule(), LanguageLevel.JDK_24, () -> doTest(LanguageLevel.JDK_25));
    }

    @Test
    public void testModuleImportStatement() {
      IdeaTestUtil.withLevel(daemon().getFixture().getModule(), LanguageLevel.JDK_24, () -> doTest(LanguageLevel.JDK_25));
    }

    @Test
    public void testImplicitClass() {
      IdeaTestUtil.withLevel(daemon().getFixture().getModule(), LanguageLevel.JDK_24, () -> doTest(LanguageLevel.JDK_25));
    }
  }

  @TestApplication
  public static class IncreaseLanguageLevel23FixTest extends IncreaseLanguageLevelFixTest {
    private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(descriptor(23));
    private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = daemonFixture(projectFixture);

    @Override
    protected LightDaemonAnalyzerFixture daemon() {
      return daemonFixture.get();
    }

    @Test
    public void testVarLocal() {
      doTest(LanguageLevel.JDK_10);
    }

    @Test
    public void testVarLambda() {
      doTest(LanguageLevel.JDK_11);
    }

    @Test
    public void testRecordTopLevel() {
      IdeaTestUtil.withLevel(daemon().getFixture().getModule(), LanguageLevel.JDK_15, () -> doTest(LanguageLevel.JDK_16));
    }

    @Test
    public void testRecordInClass() {
      doTest(LanguageLevel.JDK_16);
    }

    @Test
    public void testRecordInMethod() {
      doTest(LanguageLevel.JDK_16);
    }

    @Test
    public void testSealedClasses() {
      doTest(LanguageLevel.JDK_17);
    }

    @Test
    public void testInstanceofWithPrimitives() {
      IdeaTestUtil.withLevel(daemon().getFixture().getModule(), LanguageLevel.JDK_21, () -> doTest(JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.getMinimumLevel()));
    }

    @Test
    public void testSwitchWithPrimitiveSelectorBoolean() {
      IdeaTestUtil.withLevel(daemon().getFixture().getModule(), LanguageLevel.JDK_21, () -> doTest(JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.getMinimumLevel()));
    }

    @Test
    public void testSwitchWithPrimitiveSelectorByte() {
      IdeaTestUtil.withLevel(daemon().getFixture().getModule(), LanguageLevel.JDK_21, () -> doTestNotExpected(JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.getMinimumLevel()));
    }

    @Test
    public void testSwitchWithPrimitiveSelectorChar() {
      IdeaTestUtil.withLevel(daemon().getFixture().getModule(), LanguageLevel.JDK_21, () -> doTestNotExpected(JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.getMinimumLevel()));
    }

    @Test
    public void testSwitchWithPrimitiveSelectorDouble() {
      IdeaTestUtil.withLevel(daemon().getFixture().getModule(), LanguageLevel.JDK_21, () -> doTest(JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.getMinimumLevel()));
    }

    @Test
    public void testSwitchWithPrimitiveSelectorFloat() {
      IdeaTestUtil.withLevel(daemon().getFixture().getModule(), LanguageLevel.JDK_21, () -> doTest(JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.getMinimumLevel()));
    }

    @Test
    public void testSwitchWithPrimitiveSelectorInt() {
      IdeaTestUtil.withLevel(daemon().getFixture().getModule(), LanguageLevel.JDK_21, () -> doTestNotExpected(JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.getMinimumLevel()));
    }

    @Test
    public void testSwitchWithPrimitiveSelectorLong() {
      IdeaTestUtil.withLevel(daemon().getFixture().getModule(), LanguageLevel.JDK_21, () -> doTest(JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.getMinimumLevel()));
    }

    @Test
    public void testSwitchWithPrimitiveSelectorShort() {
      IdeaTestUtil.withLevel(daemon().getFixture().getModule(), LanguageLevel.JDK_21, () -> doTestNotExpected(JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.getMinimumLevel()));
    }

    @Test
    public void testDeconstructionWithPrimitives() {
      IdeaTestUtil.withLevel(daemon().getFixture().getModule(), LanguageLevel.JDK_21, () -> doTest(JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.getMinimumLevel()));
    }

    @Test
    public void testSwitchWithPrimitivePattern() {
      IdeaTestUtil.withLevel(daemon().getFixture().getModule(), LanguageLevel.JDK_21, () -> doTest(JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.getMinimumLevel()));
    }

    @Test
    public void testSwitchWithPrimitivePatternWithJava28() {
      IdeaTestUtil.withLevel(daemon().getFixture().getModule(), LanguageLevel.JDK_28, () -> doTest(LanguageLevel.JDK_28_PREVIEW, false));
    }

    @Test
    public void testSwitchWithPrimitiveSelectorAndPattern() {
      IdeaTestUtil.withLevel(daemon().getFixture().getModule(), LanguageLevel.JDK_21, () -> doTest(JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.getMinimumLevel()));
    }

    @Test
    public void testImplicitClass() {
      IdeaTestUtil.withLevel(daemon().getFixture().getModule(), LanguageLevel.JDK_21, () -> doTest(LanguageLevel.JDK_23_PREVIEW));
    }
  }

  private static LightProjectDescriptor descriptor(int jdkVersion) {
    return lightJavaTestDescriptor(LanguageLevel.JDK_1_8, () -> IdeaTestUtil.getMockJdk(JavaVersion.compose(jdkVersion)));
  }

  private static TestFixture<LightDaemonAnalyzerFixture> daemonFixture(TestFixture<LightJavaProject> projectFixture) {
    return lightDaemonAnalyzerFixture(projectFixture, () -> List.of(), LightDaemonAnalyzerOptions.DEFAULT.withBasePath(
      "/java/java-tests/testData/codeInsight/daemonCodeAnalyzer/increaseLanguageLevel/"));
  }

  protected abstract LightDaemonAnalyzerFixture daemon();

  protected void doTest(LanguageLevel level) {
    doTest(level, true);
  }

  protected void doTestNotExpected(LanguageLevel level) {
    doTest(level, false);
  }

  protected void doTest(LanguageLevel level, boolean expected) {
    LightDaemonAnalyzerFixture daemon = daemon();
    LightJavaCodeInsightTestFixture myFixture = daemon.getFixture();
    daemon.configureByFile(myFixture.getTestName(false) + ".java");
    daemon.doHighlighting();
    Editor editor = myFixture.getEditor();
    PsiFile file = myFixture.getFile();
    EdtTestUtil.runInEdtAndWait(() -> {
      List<IntentionAction> actions = CodeInsightTestFixtureImpl.getAvailableIntentions(editor, file);
      String message = JavaBundle.message("set.language.level.to.0", level.getPresentableText());
      IntentionAction foundAction = ContainerUtil.find(actions, act -> act.getText().equals(message));
      if (foundAction == null && expected) {
        LanguageLevel foundLevel = ContainerUtil.find(
          LanguageLevel.getEntries(),
          l -> ContainerUtil.exists(
            actions,
            act -> act.getText().equals(JavaBundle.message("set.language.level.to.0", l.getPresentableText()))
          ));
        if (foundLevel != null) {
          fail("Expected level: " + level + "; actual: " + foundLevel);
        }
        else {
          fail("Action " + message + " not found");
        }
      }
      else if (foundAction != null && !expected) {
        String actualPreview = IntentionPreviewPopupUpdateProcessor.getPreviewContent(myFixture.getProject(), foundAction, file, editor);
        JavaVersion javaVersion = level.toJavaVersion();
        String expectedPreview = JavaBundle.message("increase.language.level.preview.description", myFixture.getModule().getName(), javaVersion);
        assertEquals(expectedPreview, actualPreview, foundAction.getText());
      }
    });
  }
}
