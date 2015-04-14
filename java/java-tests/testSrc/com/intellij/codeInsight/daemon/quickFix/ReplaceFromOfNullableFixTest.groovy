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

/*
 * User: anna
 * Date: 21-Mar-2008
 */
package com.intellij.codeInsight.daemon.quickFix
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.dataFlow.DataFlowInspection
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.IdeaTestUtil
import org.jetbrains.annotations.NotNull

public class ReplaceFromOfNullableFixTest extends LightQuickFixParameterizedTestCase {
  @NotNull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return [new DataFlowInspection()] as LocalInspectionTool[]
  }

  public void test() throws Exception {
     doAllTests();
   }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/replaceFromOfNullable";
  }

  static void addGuavaOptional() {
    if (JavaPsiFacade.getInstance(project).findClass("com.google.common.base.Optional", GlobalSearchScope.allScope(project))) {
      return
    }

    WriteCommandAction.runWriteCommandAction(project) {
      VirtualFile optional = getSourceRoot()
        .createChildDirectory(this, "com")
        .createChildDirectory(this, "google")
        .createChildDirectory(this, "common")
        .createChildDirectory(this, "base")
        .createChildData(this, "Optional.java");
      VfsUtil.saveText(optional, """
package com.google.common.base;
public abstract class Optional<T> {
  public static <T> Optional<T> absent() { }

  public static <T> Optional<T> of(T reference) { }

  public static <T> Optional<T> fromNullable(@Nullable T nullableReference) { }
}
""")
    }
  }

  @Override
  protected void beforeActionStarted(String testName, String contents) {
    if (testName.contains("Guava")) {
      addGuavaOptional()
    }
    super.beforeActionStarted(testName, contents)
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk18();
  }
}