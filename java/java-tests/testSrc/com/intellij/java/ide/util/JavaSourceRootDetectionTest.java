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
package com.intellij.java.ide.util;

import com.intellij.JavaTestUtil;
import com.intellij.ide.util.projectWizard.importSources.JavaModuleSourceRoot;
import com.intellij.ide.util.projectWizard.importSources.JavaSourceRootDetectionUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class JavaSourceRootDetectionTest {
  private String myTestName;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    myTestName = PlatformTestUtil.getTestName(testInfo.getTestMethod().orElseThrow().getName(), true);
  }

  @Test
  public void testSimple() {
    doTest("src", "");
  }

  @Test
  public void testWithPrefix() {
    doTest("", "xxx.yyy");
  }

  @Test
  public void testTwoRoots() {
    doTest("src1", "", "src2", "xyz");
  }

  @Test
  public void testDefaultPackage() {
    doTest("src", "");
  }

  @Test
  public void testDefaultPackageWithImport() {
    doTest("src", "");
  }

  @Test
  public void testGarbage() {
    doTest();
  }

  @Test
  public void testFileWithBom() {
    doTest("src", "");
  }

  @Test
  public void testPackageWithAnnotation() {
    doTest("src", "");
  }

  private void doTest(String... expected) {
    final String dirPath = JavaTestUtil.getJavaTestDataPath() + FileUtil.toSystemDependentName("/ide/sourceRootDetection/" + myTestName);
    final File dir = new File(dirPath);
    assertTrue(dir.isDirectory());
    final List<Pair<File, String>> actual = new ArrayList<>();
    for (JavaModuleSourceRoot root : JavaSourceRootDetectionUtil.suggestRoots(dir)) {
      actual.add(Pair.create(root.getDirectory(), root.getPackagePrefix()));
    }
    List<Pair<File, String>> expectedList = new ArrayList<>();
    for (int i = 0; i < expected.length / 2; i++) {
      expectedList.add(Pair.create(new File(dir, expected[2 * i]), expected[2 * i + 1]));
    }
    assertThat(actual).containsExactlyInAnyOrderElementsOf(expectedList);
  }
}
