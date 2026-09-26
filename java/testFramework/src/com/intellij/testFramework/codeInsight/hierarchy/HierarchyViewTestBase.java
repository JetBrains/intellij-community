// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
                                 String @NotNull ... fileNames) {
    configure(fileNames);
    String verificationFilePath = getTestDataPath() + "/" + getBasePath() + "/verification.xml";
    try {
      HierarchyViewTestFixture.doHierarchyTest(treeStructure.get(), comparator, new File(verificationFilePath));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
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
