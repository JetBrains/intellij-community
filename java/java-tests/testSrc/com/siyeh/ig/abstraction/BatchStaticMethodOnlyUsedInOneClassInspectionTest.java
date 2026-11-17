/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.siyeh.ig.abstraction;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.JavaProjectRootsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.JavaInspectionFixture;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.JavaInspectionFixtureKt.javaInspectionFixture;
import static com.intellij.testFramework.JavaInspectionFixtureKt.javaInspectionProjectFixture;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class BatchStaticMethodOnlyUsedInOneClassInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = javaInspectionProjectFixture();
  private final TestFixture<JavaInspectionFixture> javaInspection = javaInspectionFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/ig");

  @Test
  public void test() {
    JavaCodeInsightTestFixture myFixture = javaInspection.get().getFixture();
    PsiFile file = myFixture.addFileToProject(
      "genSrc/a/Something.java",
      """
        package a;
        public class Something {
            public static String s2 = One.t;
        }
        """
    );

    myFixture.allowTreeAccessForFile(file.getVirtualFile());

    Project project = myFixture.getProject();

    VirtualFile genSrc = ProjectUtil.guessProjectDir(project).findChild("genSrc");

    JavaSourceRootProperties properties = JpsJavaExtensionService.getInstance().createSourceRootProperties("", true);
    PsiTestUtil.addSourceRoot(myFixture.getModule(), genSrc, JavaSourceRootType.SOURCE, properties);

    PsiClass buildConfig = ReadAction.computeBlocking(() -> myFixture.findClass("a.Something"));
    assertTrue(ReadAction.computeBlocking(() -> JavaProjectRootsUtil.isInGeneratedCode(buildConfig.getContainingFile().getVirtualFile(), project)));

    javaInspection.get().doTest("com/siyeh/igtest/abstraction/static_method_only_used_in_one_class/global", new StaticMethodOnlyUsedInOneClassInspection());
  }
}
