// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class AnalysisScopeTest {
  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture = heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath());

  @Test
  public void testGeneratedSourceRoot() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    VirtualFile genRoot = fixture.createTempVirtualDir();
    VirtualFile srcRoot = fixture.createTempVirtualDir();
    JavaSourceRootProperties properties = JpsJavaExtensionService.getInstance().createSourceRootProperties("", true);
    PsiTestUtil.addSourceRoot(fixture.getModule(), genRoot, JavaSourceRootType.SOURCE, properties);
    PsiTestUtil.addSourceRoot(fixture.getModule(), srcRoot);
    VirtualFile genClass = VfsTestUtil.createFile(genRoot, "Gen.java", "class Gen{}");
    VirtualFile srcClass = VfsTestUtil.createFile(srcRoot, "Src.java", "class Src{}");
    ReadAction.runBlocking(() -> {
      AnalysisScope scope = new AnalysisScope(fixture.getModule());
      assertTrue(scope.contains(srcClass));
      assertFalse(scope.contains(genClass));

      // include generated sources when explicitly requested
      AnalysisScope dirScope = new AnalysisScope(fixture.getPsiManager().findDirectory(genRoot));
      assertTrue(dirScope.contains(genClass));
    });
  }

  @Test
  public void testDirectoryScope() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    VirtualFile srcRoot = fixture.createTempVirtualDir();
    PsiTestUtil.addSourceRoot(fixture.getModule(), srcRoot);
    VirtualFile dir = VfsTestUtil.createDir(srcRoot, "com/pany/um/brella");
    VirtualFile outside = VfsTestUtil.createFile(dir.getParent(), "Outside.java", "class Outside {}");
    VirtualFile inside = VfsTestUtil.createFile(dir, "Inside.java", "class Inside {}");

    ReadAction.runBlocking(() -> {
      AnalysisScope dirScope = new AnalysisScope(fixture.getPsiManager().findDirectory(dir), fixture.getModule());
      assertTrue(dirScope.contains(inside));
      assertFalse(dirScope.contains(outside));

      AnalysisScope fileScope = new AnalysisScope(fixture.getPsiManager().findFile(inside), fixture.getModule());
      assertTrue(fileScope.contains(inside));
      assertFalse(fileScope.contains(outside));
    });
  }
}
