/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package com.intellij.java.codeInsight.daemon.quickFix

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.dataFlow.DataFlowInspection
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull

@CompileStatic
class ReplaceFromOfNullableFixTest extends LightQuickFixParameterizedTestCase {
  @NotNull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return [new DataFlowInspection()] as LocalInspectionTool[]
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/replaceFromOfNullable"
  }

  static void addGuavaOptional(Project project) {
    WriteCommandAction.runWriteCommandAction(project) {
      VirtualFile optional = getSourceRoot()
        .createChildDirectory(this, "com")
        .createChildDirectory(this, "google")
        .createChildDirectory(this, "common")
        .createChildDirectory(this, "base")
        .createChildData(this, "Optional.java")
      VfsUtil.saveText(optional, """
package com.google.common.base;
public abstract class Optional<T> {
  public static <T> Optional<T> absent() { }

  public static <T> Optional<T> of(@org.jetbrains.annotations.NotNull T reference) { }

  public static <T> Optional<T> fromNullable(T nullableReference) { }
}
""")
    }
  }

  static void cleanupGuava(Project project) {
    WriteCommandAction.runWriteCommandAction(project) {
      getSourceRoot().findChild("com")?.delete(this)
    }
  }

  @Override
  protected void beforeActionStarted(String testName, String contents) {
    if (testName.contains("Guava")) {
      addGuavaOptional(project)
    }
    super.beforeActionStarted(testName, contents)
  }

  @Override
  protected void afterActionCompleted(String testName, String contents) {
    cleanupGuava(project)
    super.afterActionCompleted(testName, contents)
  }

}