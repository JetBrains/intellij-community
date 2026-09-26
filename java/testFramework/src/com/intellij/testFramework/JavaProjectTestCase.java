// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.impl.JavaPsiFacadeEx;
import org.jetbrains.annotations.NotNull;

public abstract class JavaProjectTestCase extends HeavyPlatformTestCase {
  protected JavaPsiFacadeEx myJavaFacade;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    IdeaTestUtil.setProjectLanguageLevel(getProject(), getProjectLanguageLevel());
    myJavaFacade = JavaPsiFacadeEx.getInstanceEx(myProject);
  }

  protected @NotNull LanguageLevel getProjectLanguageLevel() {
    return LanguageLevel.JDK_1_6;
  }

  @Override
  protected void tearDown() throws Exception {
    myJavaFacade = null;
    super.tearDown();
  }

  public final @NotNull JavaPsiFacadeEx getJavaFacade() {
    return myJavaFacade;
  }

  @Override
  protected Sdk getTestProjectJdk() {
    return IdeaTestUtil.getMockJdk17();
  }

  @Override
  protected @NotNull ModuleType<?> getModuleType() {
    return JavaModuleType.getModuleType();
  }
}
