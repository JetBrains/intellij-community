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

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.JavaProjectRootsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PsiTestUtil;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;

/**
 * @author Bas Leijdekkers
 */
public class StaticMethodOnlyUsedInOneClassInspectionTest extends LightJavaInspectionTestCase {

  public void testStaticMethodOnlyUsedInOneClass() {
    doTest();
  }

  public void testStaticMethodInSourceCode() {
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

    PsiClass buildConfig = myFixture.findClass("com.siyeh.igtest.abstraction.Something");
    assertTrue(JavaProjectRootsUtil.isInGeneratedCode(buildConfig.getContainingFile().getVirtualFile(), project));
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new StaticMethodOnlyUsedInOneClassInspection();
  }
}