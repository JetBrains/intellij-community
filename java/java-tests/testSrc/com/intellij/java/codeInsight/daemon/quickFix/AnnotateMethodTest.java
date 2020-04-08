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

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.nullable.NullableStuffInspection;
import com.intellij.openapi.util.Disposer;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;

public class AnnotateMethodTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/annotateMethod";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    if (getTestName(false).contains("TypeUse")) {
      NullableNotNullManager nnnManager = NullableNotNullManager.getInstance(getProject());
      String prevNullable = nnnManager.getDefaultNullable();
      String prevNotNull = nnnManager.getDefaultNotNull();
      nnnManager.setNotNulls("typeUse.NotNull");
      nnnManager.setNullables("typeUse.Nullable");
      nnnManager.setDefaultNotNull("typeUse.NotNull");
      nnnManager.setDefaultNullable("typeUse.Nullable");
      Disposer.register(getTestRootDisposable(), () -> {
        nnnManager.setNotNulls();
        nnnManager.setNullables();
        nnnManager.setDefaultNotNull(prevNotNull);
        nnnManager.setDefaultNullable(prevNullable);
      });
    }
  }

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new NullableStuffInspection()};
  }

  @Override
  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_1_8;
  }
}