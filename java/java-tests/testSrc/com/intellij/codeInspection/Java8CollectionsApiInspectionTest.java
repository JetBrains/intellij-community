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
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.java18api.Java8CollectionsApiInspection;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Batkovich
 */
public class Java8CollectionsApiInspectionTest extends LightQuickFixParameterizedTestCase {
  private Java8CollectionsApiInspection myInspection;

  @NotNull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{myInspection};
  }

  public void setUp() throws Exception {
    myInspection = new Java8CollectionsApiInspection();
    myInspection.myReportContainsCondition = true;
    super.setUp();
  }

  public void test() throws Exception {
    doAllTests();
  }

  @Override
  protected String getBasePath() {
    return "/inspection/java8CollectionsApi";
  }
}
