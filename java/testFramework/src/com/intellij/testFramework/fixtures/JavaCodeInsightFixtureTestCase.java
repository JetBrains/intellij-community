// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.fixtures;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.TestIndexingModeSupporter;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;

@TestDataPath("$CONTENT_ROOT/testData")
public abstract class JavaCodeInsightFixtureTestCase extends UsefulTestCase implements TestIndexingModeSupporter {
  protected JavaCodeInsightTestFixture myFixture;
  private @NotNull IndexingMode myIndexingMode = IndexingMode.SMART;

  @Override
  public @NotNull Disposable getTestRootDisposable() {
    return myFixture == null ? super.getTestRootDisposable() : myFixture.getTestRootDisposable();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName());
    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
    myFixture = JavaIndexingModeCodeInsightTestFixture.Companion.wrapFixture(myFixture, getIndexingMode());
    JavaModuleFixtureBuilder<?> moduleFixtureBuilder = projectBuilder.addModule(JavaModuleFixtureBuilder.class);
    if (toAddSourceRoot()) {
      moduleFixtureBuilder.addSourceContentRoot(myFixture.getTempDirPath());
    }
    else {
      moduleFixtureBuilder.addContentRoot(myFixture.getTempDirPath());
    }
    tuneFixture(moduleFixtureBuilder);

    myFixture.setTestDataPath(getTestDataPath());
    myFixture.setUp();
    IdeaTestUtil.setProjectLanguageLevel(getProject(), LanguageLevel.JDK_1_6);
  }

  protected boolean toAddSourceRoot() {
    return true;
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myFixture.tearDown();
    }
    catch (Throwable e) {
      addSuppressedException(e);
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
  protected @NonNls String getBasePath() {
    return "";
  }

  /**
   * Return absolute path to the test data. Not intended to be overridden.
   *
   * @return absolute path to the test data.
   */
  protected @NonNls String getTestDataPath() {
    return PathManager.getHomePath().replace(File.separatorChar, '/') + getBasePath();
  }

  protected void tuneFixture(JavaModuleFixtureBuilder<?> moduleBuilder) throws Exception {}

  protected Project getProject() {
    return myFixture.getProject();
  }

  protected PsiManagerEx getPsiManager() {
    return PsiManagerEx.getInstanceEx(getProject());
  }

  public PsiElementFactory getElementFactory() {
    return JavaPsiFacade.getElementFactory(getProject());
  }

  protected Module getModule() {
    return myFixture.getModule();
  }

  @Override
  public void setIndexingMode(@NotNull IndexingMode mode) {
    myIndexingMode = mode;
  }

  @Override
  public @NotNull IndexingMode getIndexingMode() {
    return myIndexingMode;
  }
}
