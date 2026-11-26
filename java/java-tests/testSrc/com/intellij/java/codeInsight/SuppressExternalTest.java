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

package com.intellij.java.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.sillyAssignment.SillyAssignmentInspection;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JavaModuleExternalPaths;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;

import static com.intellij.testFramework.JavaCodeInsightFixtureKt.javaProjectBuilderCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Support for suppressions in external annotations is dropped. We may reconsider this later
 * when external annotations will be supported in ModCommands
 */
@TestApplication
@Disabled("Support for suppressions in external annotations is dropped. We may reconsider this later when external annotations will be supported in ModCommands")
public class SuppressExternalTest {
  private final TestFixture<JavaCodeInsightTestFixture> codeInsightFixture =
    javaProjectBuilderCodeInsightFixture(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/externalAnnotations", (testFixtureBuilder, fixture) -> {
      final JavaModuleFixtureBuilder builder = testFixtureBuilder.addModule(JavaModuleFixtureBuilder.class);
      new File(fixture.getTempDirPath() + "/src/").mkdir();
      builder.addContentRoot(fixture.getTempDirPath()).addSourceRoot("src");
      builder.setMockJdkLevel(JavaModuleFixtureBuilder.MockJdkLevel.jdk15);
    });

  private LanguageLevel myLanguageLevel;

  @BeforeEach
  void setUp() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new SillyAssignmentInspection());

    addAnnotationsModuleRoot();

    final JavaPsiFacade facade = JavaPsiFacade.getInstance(myFixture.getProject());
    myLanguageLevel = LanguageLevelProjectExtension.getInstance(facade.getProject()).getLanguageLevel();
    IdeaTestUtil.setProjectLanguageLevel(facade.getProject(), LanguageLevel.JDK_1_5);
  }

  @AfterEach
  void tearDown() {
    if (myLanguageLevel != null) {
      IdeaTestUtil.setProjectLanguageLevel(codeInsightFixture.get().getProject(), myLanguageLevel);
    }
  }


  private void addAnnotationsModuleRoot() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.copyDirectoryToProject("content/anno/suppressed", "content/anno/suppressed");
    WriteAction.runAndWait(() -> {
      final Module module = myFixture.getModule();
      final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
      final String url = VfsUtilCore.pathToUrl(myFixture.getTempDirPath() + "/content/anno");
      model.getModuleExtension(JavaModuleExternalPaths.class).setExternalAnnotationUrls(new String[]{url});
      model.commit();
    });
  }


  private void doTest(String testName) {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      final IntentionAction action = myFixture.getAvailableIntention("Suppress for method", "src/suppressed/" + testName + ".java");
      assertNotNull(action);
      Project project = myFixture.getProject();
      JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(project);
      javaSettings.USE_EXTERNAL_ANNOTATIONS = true;
      myFixture.launchAction(action);
      myFixture.checkResultByFile("content/anno/suppressed/annotations.xml", "content/anno/suppressed/annotations" + testName + "_after.xml", true);
    });
  }


  @Test
  public void testNewSuppress() {
    doTest("NewSuppress");
  }

  @Test
  public void testExistingExternalName() {
    doTest("ExistingExternalName");
  }

  @Test
  public void testSecondSuppression() {
    doTest("SecondSuppression");
  }

}
