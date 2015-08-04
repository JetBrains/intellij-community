/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 17-Jun-2010
 */
package com.intellij.codeInsight;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.sillyAssignment.SillyAssignmentInspection;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.JavaModuleExternalPaths;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.*;

import java.io.File;
import java.io.IOException;

public class SuppressExternalTest extends UsefulTestCase {
  protected CodeInsightTestFixture myFixture;

  private LanguageLevel myLanguageLevel;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    final IdeaTestFixtureFactory fixtureFactory = IdeaTestFixtureFactory.getFixtureFactory();
    final TestFixtureBuilder<IdeaProjectTestFixture> testFixtureBuilder = fixtureFactory.createFixtureBuilder(getName());
    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(testFixtureBuilder.getFixture());
    myFixture.setTestDataPath(PathManagerEx.getTestDataPath() + "/codeInsight/externalAnnotations");
    final JavaModuleFixtureBuilder builder = testFixtureBuilder.addModule(JavaModuleFixtureBuilder.class);
    new File(myFixture.getTempDirPath() + "/src/").mkdir();
    builder.addContentRoot(myFixture.getTempDirPath()).addSourceRoot("src");
    builder.setMockJdkLevel(JavaModuleFixtureBuilder.MockJdkLevel.jdk15);
    myFixture.setUp();
    myFixture.enableInspections(new SillyAssignmentInspection());

    addAnnotationsModuleRoot();

    final JavaPsiFacade facade = JavaPsiFacade.getInstance(myFixture.getProject());
    myLanguageLevel = LanguageLevelProjectExtension.getInstance(facade.getProject()).getLanguageLevel();
    LanguageLevelProjectExtension.getInstance(facade.getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
  }

  private void addAnnotationsModuleRoot() throws IOException {
    myFixture.copyDirectoryToProject("content/anno/suppressed", "content/anno/suppressed");
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        final Module module = myFixture.getModule();
        final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
        final String url = VfsUtilCore.pathToUrl(myFixture.getTempDirPath() + "/content/anno");
        model.getModuleExtension(JavaModuleExternalPaths.class).setExternalAnnotationUrls(new String[]{url});
        model.commit();
      }
    });
  }


  @Override
  public void tearDown() throws Exception {
    LanguageLevelProjectExtension.getInstance(myFixture.getProject()).setLanguageLevel(myLanguageLevel);

    try {
      myFixture.tearDown();
    }
    finally {
      myFixture = null;
      super.tearDown();
    }
  }


  private void doTest(String testName) throws Exception {
    final IntentionAction action = myFixture.getAvailableIntention("Suppress for method", "src/suppressed/" + testName + ".java");
    assertNotNull(action);
    myFixture.launchAction(action);
    myFixture.checkResultByFile("content/anno/suppressed/annotations.xml", "content/anno/suppressed/annotations" + testName + "_after.xml", true);
  }


  public void testNewSuppress() throws Throwable {
    doTest("NewSuppress");
  }

  public void testExistingExternalName() throws Exception {
    doTest("ExistingExternalName");
  }

  public void testSecondSuppression() throws Exception {
    doTest("SecondSuppression");
  }

}
