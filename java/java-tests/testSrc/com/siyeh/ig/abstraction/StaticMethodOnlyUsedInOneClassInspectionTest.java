/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.siyeh.ig.abstraction;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.JavaProjectRootsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class StaticMethodOnlyUsedInOneClassInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new StaticMethodOnlyUsedInOneClassInspection());

  @Test
  public void testStaticMethodOnlyUsedInOneClass() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testStaticMethodInSourceCode() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    PsiFile file = myFixture.addFileToProject(
      "genSrc/com/siyeh/igtest/abstraction/Something.java",
      """
        package com.siyeh.igtest.abstraction;
        public class Something {
            public static String s2 = StaticMethodInSourceCode.t;
        }
        """
    );

    myFixture.allowTreeAccessForFile(file.getVirtualFile());

    Project project = myFixture.getProject();

    VirtualFile genSrc = ProjectUtil.guessProjectDir(project).findChild("genSrc");

    JavaSourceRootProperties properties = JpsJavaExtensionService.getInstance().createSourceRootProperties("", true);
    PsiTestUtil.addSourceRoot(myFixture.getModule(), genSrc, JavaSourceRootType.SOURCE, properties);

    PsiClass buildConfig = ReadAction.computeBlocking(() -> myFixture.findClass("com.siyeh.igtest.abstraction.Something"));
    assertTrue(ReadAction.computeBlocking(() -> JavaProjectRootsUtil.isInGeneratedCode(buildConfig.getContainingFile().getVirtualFile(), project)));
    inspectionFixture.get().doTest();
  }
}