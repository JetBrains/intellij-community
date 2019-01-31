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

import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class UnusedDeclarationInProjectTest extends AbstractUnusedDeclarationTest {

  @NotNull
  @Override
  protected AnalysisScope createAnalysisScope(VirtualFile sourceDir) {
    return new AnalysisScope(getProject());
  }

  public void testInstantiatedInTestOnly() {
    myTool.setTestEntryPoints(false);
    doTest();
  }

  public void testInstantiatedInTestOnlyStatic() {
    myTool.setTestEntryPoints(false);
    doTest();
  }

  public void testIgnoreUnusedFields() {
    myTool.getSharedLocalInspectionTool().FIELD = false;
    doTest();
  }
}
