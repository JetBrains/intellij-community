// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.impl.JavaPsiFacadeEx;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Comparator;

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

  protected static void sortClassesByName(@NotNull PsiClass[] classes) {
    Arrays.sort(classes, Comparator.comparing(NavigationItem::getName));
  }
}
