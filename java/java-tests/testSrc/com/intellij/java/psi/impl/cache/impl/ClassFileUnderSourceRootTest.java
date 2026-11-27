// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.impl.cache.impl;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.io.PathKt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class ClassFileUnderSourceRootTest {
  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture = heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath());

  @BeforeEach
  void setUp() throws Exception {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();

    VirtualFile root = fixture.createTempVirtualDir();
    Path dir = root.toNioPath();

    PathKt.write(dir.resolve("p/A.java"), "package p;\npublic class A { }");
    FileUtil.copy(new File(JavaTestUtil.getJavaTestDataPath() + "/psi/cls/repo/pack/MyClass.class"), dir.resolve("pack/MyClass.class").toFile());

    root.refresh(false, true);
    assertEquals(2, root.getChildren().length);

    WriteAction.runAndWait(() -> {
      ModuleRootModificationUtil.addModuleLibrary(fixture.getModule(), root.getUrl());
      PsiTestUtil.addSourceRoot(fixture.getModule(), root);
    });
  }

  @Test
  public void testFindClasses() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    ReadAction.runBlocking(() -> {
      PsiClass srcClass = fixture.getJavaFacade().findClass("p.A");
      assertNotNull(srcClass);
      assertEquals("p.A", srcClass.getQualifiedName());

      PsiClass pkgClass = fixture.getJavaFacade().findClass("pack.MyClass");
      assertNotNull(pkgClass);
      assertEquals("pack.MyClass", pkgClass.getQualifiedName());
    });
  }
}
