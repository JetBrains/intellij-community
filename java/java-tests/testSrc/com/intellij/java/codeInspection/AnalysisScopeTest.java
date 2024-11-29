// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.JavaModuleTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.VfsTestUtil;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;

public class AnalysisScopeTest extends JavaModuleTestCase {
  public void testGeneratedSourceRoot() throws Exception {
    VirtualFile genRoot = getVirtualFile(createTempDir("genSrcRoot"));
    VirtualFile srcRoot = getVirtualFile(createTempDir("srcRoot"));
    JavaSourceRootProperties properties = JpsJavaExtensionService.getInstance().createSourceRootProperties("", true);
    PsiTestUtil.addSourceRoot(myModule, genRoot, JavaSourceRootType.SOURCE, properties);
    PsiTestUtil.addSourceRoot(myModule, srcRoot);
    VirtualFile genClass = VfsTestUtil.createFile(genRoot, "Gen.java", "class Gen{}");
    VirtualFile srcClass = VfsTestUtil.createFile(srcRoot, "Src.java", "class Src{}");
    AnalysisScope scope = new AnalysisScope(myModule);
    assertTrue(scope.contains(srcClass));
    assertFalse(scope.contains(genClass));

    // include generated sources when explicitly requested
    AnalysisScope dirScope = new AnalysisScope(getPsiManager().findDirectory(genRoot));
    assertTrue(dirScope.contains(genClass));
  }

  public void testDirectoryScope() throws Exception {
    VirtualFile srcRoot = getVirtualFile(createTempDir("srcRoot"));
    PsiTestUtil.addSourceRoot(myModule, srcRoot);
    VirtualFile dir = VfsTestUtil.createDir(srcRoot, "com/pany/um/brella");
    VirtualFile outside = VfsTestUtil.createFile(dir.getParent(), "Outside.java", "class Outside {}");
    VirtualFile inside = VfsTestUtil.createFile(dir, "Inside.java", "class Inside {}");

    AnalysisScope dirScope = new AnalysisScope(getPsiManager().findDirectory(dir), myModule);
    assertTrue(dirScope.contains(inside));
    assertFalse(dirScope.contains(outside));

    AnalysisScope fileScope = new AnalysisScope(getPsiManager().findFile(inside), myModule);
    assertTrue(fileScope.contains(inside));
    assertFalse(fileScope.contains(outside));
  }
}
