/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class LoopConditionNotUpdatedInsideLoopInspectionTest extends LightJavaInspectionTestCase {

  public void testLoopConditionNotUpdatedInsideLoop() {
    doTest();
  }

  @Override
  protected LocalInspectionTool getInspection() {
    return new LoopConditionNotUpdatedInsideLoopInspection();
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_9_ANNOTATED;
  }

  @Override
  protected String getBasePath() {
    return "/java/java-tests/testData/ig/com/siyeh/igtest/controlflow/loop_condition_not_updated";
  }
}
