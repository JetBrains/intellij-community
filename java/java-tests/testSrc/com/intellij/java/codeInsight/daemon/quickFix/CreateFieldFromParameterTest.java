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
package com.intellij.java.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.LightIntentionActionTestCase;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

import static com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_8;

/**
 * @author ven
 */
public class CreateFieldFromParameterTest extends LightIntentionActionTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    JavaCodeStyleSettings.getInstance(getProject()).FIELD_NAME_PREFIX = "my";
    enableInspectionTool(new UnusedDeclarationInspectionBase());
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/createFieldFromParameter";
  }
}