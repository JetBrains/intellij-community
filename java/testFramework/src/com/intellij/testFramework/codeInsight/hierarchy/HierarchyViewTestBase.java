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

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.testFramework.ExpectedHighlightingData;
import groovy.lang.GroovyObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.function.Supplier;

/**
 * Checks tree structure for Type Hierarchy (Ctrl+H), Call Hierarchy (Ctrl+Alt+H), Method Hierarchy (Ctrl+Shift+H).
 */
public abstract class HierarchyViewTestBase extends DaemonAnalyzerTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    // BundledGroovyClassFinder tries to load this jar
    String groovyJar = PathManager.getJarPathForClass(GroovyObject.class);
    if (groovyJar != null) {
      VfsRootAccess.allowRootAccess(getTestRootDisposable(), groovyJar);
    }
  }

  protected abstract String getBasePath();

  protected void doHierarchyTest(@NotNull Supplier<? extends HierarchyTreeStructure> treeStructure,
                                 @Nullable Comparator<? super NodeDescriptor<?>> comparator,
                                 String @NotNull ... fileNames) throws IOException {
    configure(fileNames);
    String verificationFilePath = getTestDataPath() + "/" + getBasePath() + "/verification.xml";
    HierarchyViewTestFixture.doHierarchyTest(treeStructure.get(), comparator, new File(verificationFilePath));
  }

  private void configure(String @NotNull [] fileNames) {
    final String[] relFilePaths = new String[fileNames.length];
    for (int i = 0; i < fileNames.length; i++) {
      relFilePaths[i] = "/" + getBasePath() + "/" + fileNames[i];
    }
    configureByFiles(null, relFilePaths);
    ExpectedHighlightingData expectedHighlightingData = new ExpectedHighlightingData(myEditor.getDocument(), false, false, false);
    checkHighlighting(expectedHighlightingData); // ensure there are no syntax errors because they can interfere with hierarchy calculation correctness
  }
}
