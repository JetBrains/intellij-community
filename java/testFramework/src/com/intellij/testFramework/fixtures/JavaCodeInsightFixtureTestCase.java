/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.testFramework.fixtures;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import org.jetbrains.annotations.NonNls;

import java.io.File;

/**
 * @author peter
 */
public abstract class JavaCodeInsightFixtureTestCase extends UsefulTestCase{
  protected JavaCodeInsightTestFixture myFixture;
  protected Module myModule;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    final TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName());
    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
    final JavaModuleFixtureBuilder moduleFixtureBuilder = projectBuilder.addModule(JavaModuleFixtureBuilder.class);
    if (toAddSourceRoot()) {
      moduleFixtureBuilder.addSourceContentRoot(myFixture.getTempDirPath());
    } else {
      moduleFixtureBuilder.addContentRoot(myFixture.getTempDirPath());
    }
    tuneFixture(moduleFixtureBuilder);    

    myFixture.setUp();
    myFixture.setTestDataPath(getTestDataPath());
    myModule = moduleFixtureBuilder.getFixture().getModule();
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_6);
  }

  protected boolean toAddSourceRoot() {
    return true;
  }

  @Override
  protected void tearDown() throws Exception {
    myModule = null;

    try {
      myFixture.tearDown();
    }
    finally {
      myFixture = null;

      super.tearDown();
    }
  }

  /**
   * Return relative path to the test data. Path is relative to the
   * {@link PathManager#getHomePath()}
   *
   * @return relative path to the test data.
   */
  @NonNls
  protected String getBasePath() {
    return "";
  }

  /**
   * Return absolute path to the test data. Not intended to be overridden.
   *
   * @return absolute path to the test data.
   */
  @NonNls
  protected String getTestDataPath() {
    return PathManager.getHomePath().replace(File.separatorChar, '/') + getBasePath();
  }

  protected void tuneFixture(final JavaModuleFixtureBuilder moduleBuilder) throws Exception {}


  protected Project getProject() {
    return myFixture.getProject();
  }

  protected PsiManagerEx getPsiManager() {
    return (PsiManagerEx)PsiManager.getInstance(getProject());
  }

  public PsiElementFactory getElementFactory() {
    return JavaPsiFacade.getInstance(getProject()).getElementFactory();
  }
}
