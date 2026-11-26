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

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.invertBoolean.InvertBooleanProcessor;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

@TestDataPath("$CONTENT_ROOT/testData/refactoring/invertBoolean/")
@TestApplication
public class InvertBooleanTest {
  private static final String DATA_PATH = "/refactoring/invertBoolean/";
  private static final String AFTER_PREFIX = "after";
  private static final String CONFLICTS_SUFFIX = ".conflicts.txt";
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData" + DATA_PATH);

  static List<String> testFiles() {
    String testDataPath = JavaTestUtil.getJavaTestDataPath() + DATA_PATH;
    File[] files = new File(testDataPath).listFiles();
    if (files == null) {
      fail("Test files not found in " + testDataPath);
    }
    List<String> result = new ArrayList<>();
    for (File file : files) {
      String suffix = getFileSuffix(file.getName());
      if (suffix != null) {
        result.add(suffix);
      }
    }
    result.sort(null);
    return result;
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("testFiles")
  public void runSingle(String fileSuffix) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String beforeFile = getBeforeFile(fileSuffix);
    String afterFile = getAfterFile(fileSuffix);
    myFixture.configureByFile(beforeFile);

    final File testDir = new File(myFixture.getTestDataPath(), beforeFile).getParentFile();
    final boolean conflictShouldBeFound = !new File(testDir, afterFile).exists();
    myFixture.runInCommand(() -> {
      try {
        perform(myFixture);
        if (conflictShouldBeFound) {
          fail("Conflict expected.");
        }
      }
      catch (BaseRefactoringProcessor.ConflictsInTestsException exception) {
        if (!conflictShouldBeFound) {
          fail("Conflict not expected");
        }
        else {
          final File conflicts = new File(testDir, FileUtilRt.getNameWithoutExtension(fileSuffix) + CONFLICTS_SUFFIX);
          if (!conflicts.exists()) {
            fail("Conflict file " + conflicts.getPath() + " not found");
          }
          final VirtualFile conflictsFile = VfsUtil.findFileByIoFile(conflicts, false);
          assertNotNull(conflictsFile);
          assertEquals(LoadTextUtil.loadText(conflictsFile).toString(), exception.getMessage());
        }
      }

      if (!conflictShouldBeFound) {
        myFixture.checkResultByFile(afterFile);
      }
    });
  }

  private static void perform(LightJavaCodeInsightTestFixture myFixture) {
    PsiElement element = TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED);
    final PsiNamedElement namedElement = assertInstanceOf(PsiNamedElement.class, element);
    final String name = namedElement.getName();
    new InvertBooleanProcessor(namedElement, name + "Inverted").run();
  }

  private static String getAfterFile(String fileName) {
    return FileUtilRt.getNameWithoutExtension(fileName) + "_" + AFTER_PREFIX + "." + FileUtilRt.getExtension(fileName);
  }

  private static String getBeforeFile(String fileName) {
    return fileName;
  }

  private static String getFileSuffix(@NotNull String beforeFile) {
    return !beforeFile.contains(AFTER_PREFIX) && !beforeFile.endsWith(CONFLICTS_SUFFIX) ? beforeFile : null;
  }
}
