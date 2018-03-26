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
package com.intellij.testFramework.codeInsight.hierarchy;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

/**
 * Checks tree structure for Type Hierarchy (Ctrl+H), Call Hierarchy (Ctrl+Alt+H), Method Hierarchy (Ctrl+Shift+H).
 */
public abstract class HierarchyViewTestBase extends CodeInsightTestCase {
  private final HierarchyViewTestFixture myFixture = new HierarchyViewTestFixture();

  protected abstract String getBasePath();

  protected void doHierarchyTest(@NotNull Computable<HierarchyTreeStructure> treeStructureComputable,
                                 @NotNull String... fileNames) throws Exception {
    configure(fileNames);
    String expectedStructure = loadExpectedStructure();

    myFixture.doHierarchyTest(treeStructureComputable.compute(), expectedStructure);
  }

  private void configure(@NotNull String[] fileNames) {
    final String[] relFilePaths = new String[fileNames.length];
    for (int i = 0; i < fileNames.length; i++) {
      relFilePaths[i] = "/" + getBasePath() + "/" + fileNames[i];
    }
    configureByFiles(null, relFilePaths);
  }

  @NotNull
  private String loadExpectedStructure() throws IOException {
    String verificationFilePath = getTestDataPath() + "/" + getBasePath() + "/" + getTestName(false) + "_verification.xml";
    return FileUtil.loadFile(new File(verificationFilePath));
  }
}
