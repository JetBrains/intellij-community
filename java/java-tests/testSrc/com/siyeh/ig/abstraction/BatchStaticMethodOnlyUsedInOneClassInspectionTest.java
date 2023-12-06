/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.siyeh.ig.abstraction;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.JavaProjectRootsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PsiTestUtil;
import com.siyeh.ig.IGInspectionTestCase;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;

/**
 * @author Bas Leijdekkers
 */
public class BatchStaticMethodOnlyUsedInOneClassInspectionTest extends IGInspectionTestCase {

  public void test() {
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

    PsiClass buildConfig = myFixture.findClass("a.Something");
    assertTrue(JavaProjectRootsUtil.isInGeneratedCode(buildConfig.getContainingFile().getVirtualFile(), project));

    doTest("com/siyeh/igtest/abstraction/static_method_only_used_in_one_class/global", new StaticMethodOnlyUsedInOneClassInspection());
  }
}
