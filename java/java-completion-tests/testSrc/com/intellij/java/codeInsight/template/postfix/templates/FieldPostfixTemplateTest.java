// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.templates;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static com.intellij.java.codeInsight.template.postfix.templates.PostfixTemplateFixtureKt.postfixTemplateFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

/**
 * @author ignatov
 */
@TestApplication
@IndexingModeTemplate
public class FieldPostfixTemplateTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  protected final TestFixture<PostfixTemplateFixture> postfix = postfixTemplateFixture(projectFixture, "field");

  @Test
  public void testSimple() {
    doTest();
  }

  @Test
  public void testFoo() {
    doTest();
  }
  
  @Test
  public void testAnnotated() {
    doTest();
  }

  private void doTest() {
    EdtTestUtil.runInEdtAndWait(() -> {
      postfix.get().expandTemplate(useModCommandTemplates());
      checkAfterFile();
    });
  }

  protected void checkAfterFile() {
    postfix.get().checkAfterFile();
  }

  protected boolean useModCommandTemplates() {
    return false;
  }

  @NeedsIndex.SmartMode(reason = "The JUnit 3 dumb completion suite did not run the ModCommand variants")
  public static class ModFieldPostfixTemplateTest extends FieldPostfixTemplateTest {
    @Override
    protected boolean useModCommandTemplates() {
      return true;
    }

    //mod command has different an end position for caret after renaming, because it can only rename PsiNamedElement
    @Override
    protected void checkAfterFile() {
      try {
        LightJavaCodeInsightTestFixture myFixture = postfix.get().getFixture();
        String expected = FileUtil.loadFile(new File(myFixture.getTestDataPath(), myFixture.getTestName(true) + "_after.java"))
          .replace("<caret>", "");
        myFixture.checkResult(expected, true);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
