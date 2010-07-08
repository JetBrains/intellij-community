/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.testFramework;

import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.impl.JavaPsiFacadeEx;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;

/**
 * A TestCase for single PsiFile being opened in Editor conversion. See configureXXX and checkResultXXX method docs.
 */
public abstract class LightCodeInsightTestCase extends LightPlatformCodeInsightTestCase {
  private LanguageLevel myOldLanguageLevel;

  protected LightCodeInsightTestCase() {
    IdeaTestCase.initPlatformPrefix();
  }

  public static JavaPsiFacadeEx getJavaFacade() {
    return JavaPsiFacadeEx.getInstanceEx(ourProject);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myOldLanguageLevel = LanguageLevelProjectExtension.getInstance(getProject()).getLanguageLevel();
    setLanguageLevel(LanguageLevel.HIGHEST);
  }

  @Override
  protected void tearDown() throws Exception {
    setLanguageLevel(myOldLanguageLevel);
    super.tearDown();
  }

  protected static void setLanguageLevel(final LanguageLevel level) {
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(level);
  }

  @Override
  protected Sdk getProjectJDK() {
    return JavaSdkImpl.getMockJdk17();
  }

  protected ModuleType getModuleType() {
    return StdModuleTypes.JAVA;
  }
}
