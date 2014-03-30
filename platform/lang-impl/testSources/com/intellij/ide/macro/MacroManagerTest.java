/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import gnu.trove.THashMap;

import java.io.File;
import java.util.Map;

public class MacroManagerTest extends CodeInsightFixtureTestCase {
  private void doTest(String filePath, String str, String expected) throws Macro.ExecutionCancelledException {
    PsiFile file = myFixture.addFileToProject(filePath, "");
    String actual = MacroManager.getInstance().expandMacrosInString(str, false, getContext(file.getVirtualFile()));
    assertEquals(expected, actual);
  }

  public DataContext getContext(VirtualFile file) {
    Project project = myFixture.getProject();
    Map<String, Object> dataId2data = new THashMap<String, Object>();
    dataId2data.put(CommonDataKeys.PROJECT.getName(), project);
    dataId2data.put(CommonDataKeys.VIRTUAL_FILE.getName(), file);
    dataId2data.put(PlatformDataKeys.PROJECT_FILE_DIRECTORY.getName(), project.getBaseDir());
    return SimpleDataContext.getSimpleContext(dataId2data, null);
  }

  public void testFileParentDirMacro() throws Throwable {
    doTest(
      "foo/bar/baz/test.txt",
      "ans: $FileParentDir(bar)$ ",
      "ans: " + myFixture.getTempDirPath() + File.separator + "foo "
    );
  }

  public void testFileDirPathFromParentMacro() throws Throwable {
    doTest(
      "foo/bar/baz/test.txt",
      "ans: $FileDirPathFromParent(bar)$ ",
      "ans: baz/ "
    );
  }
}
