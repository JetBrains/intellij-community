// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.impl.JavaPsiFacadeEx;
import org.jetbrains.annotations.NotNull;

public abstract class JavaProjectTestCase extends HeavyPlatformTestCase {
  protected JavaPsiFacadeEx myJavaFacade;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(getProjectLanguageLevel());
    myJavaFacade = JavaPsiFacadeEx.getInstanceEx(myProject);
  }

  @NotNull
  protected LanguageLevel getProjectLanguageLevel() {
    return LanguageLevel.JDK_1_6;
  }

  @Override
  protected void tearDown() throws Exception {
    myJavaFacade = null;
    super.tearDown();
  }

  @NotNull
  public final JavaPsiFacadeEx getJavaFacade() {
    return myJavaFacade;
  }

  @Override
  protected Sdk getTestProjectJdk() {
    return IdeaTestUtil.getMockJdk17();
  }

  @Override
  protected @NotNull ModuleType<?> getModuleType() {
    return StdModuleTypes.JAVA;
  }
}
