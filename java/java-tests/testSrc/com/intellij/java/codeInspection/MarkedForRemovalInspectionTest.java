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

package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.deprecation.MarkedForRemovalInspection;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.InspectionTestCase;

/**
 * @author max
 */
public class MarkedForRemovalInspectionTest extends InspectionTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    LanguageLevelProjectExtension.getInstance(myJavaFacade.getProject()).setLanguageLevel(LanguageLevel.JDK_1_9);
    ModuleRootModificationUtil.setModuleSdk(getModule(), getTestProjectSdk());
  }

  @Override
  protected Sdk getTestProjectSdk() {
    return IdeaTestUtil.getMockJdk9();
  }

  private void doTest() {
    doTest("forRemoval/" + getTestName(true), new MarkedForRemovalInspection());
  }

  public void testForRemovalClass() {
    doTest();
  }

  public void testForRemovalField() {
    doTest();
  }

  public void testForRemovalMethod() {
    doTest();
  }

  public void testForRemovalOverride() {
    doTest();
  }

  public void testForRemovalDefaultConstructorInSuper() {
    doTest();
  }
}
