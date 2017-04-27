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
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Tagir Valeev
 */
public class SimplifyOptionalCallChainsInspectionTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected LanguageLevel getLanguageLevel() {
    return getTestName(false).endsWith("Java9.java") ? LanguageLevel.JDK_1_9 : LanguageLevel.JDK_1_8;
  }

  @Override
  protected Sdk getProjectJDK() {
    return getTestName(false).endsWith("Java9.java") ? IdeaTestUtil.getMockJdk9() : IdeaTestUtil.getMockJdk18();
  }

  @NotNull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new SimplifyOptionalCallChainsInspection()};
  }

  public void test() throws Exception {
    doAllTests();
  }

  @Override
  protected String getBasePath() {
    return "/inspection/optionalChains";
  }
}
