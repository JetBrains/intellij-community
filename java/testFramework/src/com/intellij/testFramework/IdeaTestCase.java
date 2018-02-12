/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.impl.JavaPsiFacadeEx;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * @author mike
 */
public abstract class IdeaTestCase extends PlatformTestCase {
  protected JavaPsiFacadeEx myJavaFacade;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_6);
    myJavaFacade = JavaPsiFacadeEx.getInstanceEx(myProject);
  }

  @Override
  protected void tearDown() throws Exception {
    myJavaFacade = null;
    super.tearDown();
  }

  public final JavaPsiFacadeEx getJavaFacade() {
    return myJavaFacade;
  }

  @Override
  protected Sdk getTestProjectJdk() {
    return IdeaTestUtil.getMockJdk17();
  }

  @Override
  protected ModuleType getModuleType() {
    return StdModuleTypes.JAVA;
  }

  /**
   * @deprecated calling this method is no longer necessary
   */
  public static void initPlatformPrefix() {
  }

  protected static void sortClassesByName(@NotNull PsiClass[] classes) {
    Arrays.sort(classes, (o1, o2) -> o1.getName().compareTo(o2.getName()));
  }
}
