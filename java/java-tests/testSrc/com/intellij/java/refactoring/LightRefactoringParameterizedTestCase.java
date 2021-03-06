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

package com.intellij.java.refactoring;

import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.testFramework.FileBasedTestCaseHelper;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

@RunWith(com.intellij.testFramework.Parameterized.class)
public abstract class LightRefactoringParameterizedTestCase extends LightRefactoringTestCase implements FileBasedTestCaseHelper {

  protected static final String BEFORE_PREFIX = "before";
  protected static final String AFTER_PREFIX = "after";
  protected static final String CONFLICTS_SUFFIX = ".conflicts.txt";

  protected abstract void perform();

  protected abstract String getAfterFile(String fileNameCore);
  protected abstract String getBeforeFile(String fileNameCore);

  @Test
  public void runSingle() {
    final String filePath = getBeforeFile(myFileSuffix);
    configureByFile(filePath);

    final File testDir = new File(getTestDataPath(), filePath).getParentFile();
    final String afterName = getAfterFile(myFileSuffix);
    final boolean conflictShouldBeFound = !new File(testDir, afterName).exists();
    try {
      perform();
      if (conflictShouldBeFound) {
        fail("Conflict expected.");
      }
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException exception) {
      if (!conflictShouldBeFound) {
        fail("Conflict not expected");
      } else {
        final File conflicts = new File(testDir, FileUtilRt.getNameWithoutExtension(myFileSuffix) + CONFLICTS_SUFFIX);
        if (!conflicts.exists()) {
          fail("Conflict file " + conflicts.getPath() + " not found");
        }
        final VirtualFile conflictsFile = VfsUtil.findFileByIoFile(conflicts, false);
        assertNotNull(conflictsFile);
        assertEquals(LoadTextUtil.loadText(conflictsFile).toString(), exception.getMessage());
      }
    }

    if (!conflictShouldBeFound) {
      checkResultByFile(getAfterFile(myFileSuffix));
    }
  }
}
