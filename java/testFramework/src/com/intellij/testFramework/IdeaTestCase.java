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

import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.psi.PsiClass;
import com.intellij.psi.impl.JavaPsiFacadeEx;
import org.jetbrains.annotations.NonNls;

import java.util.Arrays;
import java.util.Comparator;

/**
 * @author mike
 */
@NonNls public abstract class IdeaTestCase extends PlatformTestCase {
  @SuppressWarnings({"JUnitTestCaseWithNonTrivialConstructors"})
  protected IdeaTestCase() {
    initPlatformPrefix();
  }

  public final JavaPsiFacadeEx getJavaFacade() {
    return JavaPsiFacadeEx.getInstanceEx(myProject);
  }

  @Override
  protected Sdk getTestProjectJdk() {
    return JavaSdkImpl.getMockJdk17();
  }

  @Override
  protected ModuleType getModuleType() {
    return StdModuleTypes.JAVA;
  }

  public static void initPlatformPrefix() {
    initPlatformPrefix("com.intellij.idea.IdeaUltimateApplication", "Idea");
  }

  protected static void sortClassesByName(final PsiClass[] classes) {
    Arrays.sort(classes, new Comparator<PsiClass>() {
      public int compare(PsiClass o1, PsiClass o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });
  }
}
