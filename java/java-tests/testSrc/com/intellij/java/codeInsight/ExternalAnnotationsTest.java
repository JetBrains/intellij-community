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

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JavaModuleExternalPaths;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.*;

public class ExternalAnnotationsTest extends UsefulTestCase {
  private CodeInsightTestFixture myFixture;
  private Module myModule;
  private Project myProject;
 
  @Override
  public void setUp() throws Exception {
    super.setUp();
    final TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName());

    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
    final String dataPath = PathManagerEx.getTestDataPath() + "/codeInsight/externalAnnotations";
    myFixture.setTestDataPath(dataPath);
    final JavaModuleFixtureBuilder builder = projectBuilder.addModule(JavaModuleFixtureBuilder.class);
    builder.setMockJdkLevel(JavaModuleFixtureBuilder.MockJdkLevel.jdk15);

    myFixture.setUp();
    myModule = builder.getFixture().getModule();
    ModuleRootModificationUtil.updateModel(myModule, model -> {
      String contentUrl = VfsUtilCore.pathToUrl(myFixture.getTempDirPath());
      model.addContentEntry(contentUrl).addSourceFolder(contentUrl + "/src", false);
      final JavaModuleExternalPaths extension = model.getModuleExtension(JavaModuleExternalPaths.class);
      extension.setExternalAnnotationUrls(new String[]{VfsUtilCore.pathToUrl(myFixture.getTempDirPath() + "/content/anno")});
    });
  
    myProject = myFixture.getProject();

    CodeStyleSettingsManager.getSettings(myProject).getCustomSettings(JavaCodeStyleSettings.class).USE_EXTERNAL_ANNOTATIONS = true;
  }

  @Override
  protected void tearDown() throws Exception {
    CodeStyleSettingsManager.getSettings(myProject).getCustomSettings(JavaCodeStyleSettings.class).USE_EXTERNAL_ANNOTATIONS = false;
    try {
      myFixture.tearDown();
    }
    finally {
      myFixture = null;
      myModule = null;
      myProject = null;
  
      super.tearDown();
    }
  }

  public void testRenameClassWithExternalAnnotations() throws Exception {
    myFixture.configureByFiles("src/rename/Foo.java", "content/anno/rename/annotations.xml");

    myFixture.renameElementAtCaret("Bar");
    
    myFixture.checkResultByFile("content/anno/rename/annotations.xml",
                                "content/anno/rename/annotations_after.xml", 
                                true);
  }

  public void testBringToSrc() throws Exception {
    myFixture.configureByFiles("src/toSrc/Foo.java", "content/anno/toSrc/annotations.xml");

    IntentionAction action = myFixture.findSingleIntention("Insert '@Deprecated'");
    assertNotNull(action);
   
    myFixture.launchAction(action);
    
    myFixture.checkResultByFile("src/toSrc/Foo_after.java");
    myFixture.checkResultByFile("content/anno/toSrc/annotations.xml",
                                "content/anno/toSrc/annotations_after.xml", 
                                true);
  }

  public void testFromSrcToExternal() throws Exception {
    myFixture.configureByFiles("src/fromSrc/Foo.java", "content/anno/fromSrc/annotations.xml");

    IntentionAction action = myFixture.findSingleIntention("Annotate externally");
    assertNotNull(action);
   
    myFixture.launchAction(action);
    
    myFixture.checkResultByFile("src/fromSrc/Foo_after.java");
    myFixture.checkResultByFile("content/anno/fromSrc/annotations.xml",
                                "content/anno/fromSrc/annotations_after.xml", 
                                true);

  }
}
