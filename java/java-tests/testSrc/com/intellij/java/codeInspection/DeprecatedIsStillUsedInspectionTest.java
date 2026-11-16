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

package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.DeprecatedIsStillUsedInspection;
import com.intellij.java.testFramework.fixtures.LightJava9ModulesFixture;
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.java.testFramework.fixtures.LightJava9ModulesFixtureKt.lightJava9ModulesFixture;
import static com.intellij.java.testFramework.fixtures.LightJava9ModulesFixtureKt.lightJava9ModulesProjectFixture;

@TestApplication
public class DeprecatedIsStillUsedInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJava9ModulesProjectFixture();
  private final TestFixture<LightJava9ModulesFixture> java9 =
    lightJava9ModulesFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/inspection/deprecatedIsStillUsed");

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    myFixture.enableInspections(new DeprecatedIsStillUsedInspection());
  }

  @Test
  public void testSimple() {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    myFixture.testHighlighting(myFixture.getTestName(false) + ".java");
  }

  @Test
  public void testOverloadedAndStaticImport() {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    myFixture.addClass("""
                         package bar;
                         import static foo.OverloadedAndStaticImport.bar;
                         class Bar {
                           {
                             bar();
                           }
                         }""");
    myFixture.testHighlighting(myFixture.getTestName(false) + ".java");
  }

  @Test
  public void testDeprecatedWithManyOccurrences() {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    for (int i = 0; i < 20; i++) {
      myFixture.addClass("class MyTest" + i + "{ DeprecatedWithManyOccurrences d;}");
    }
    myFixture.testFile(myFixture.getTestName(false) + ".java").checkSymbolNames().test();
  }

  @Test
  public void testJavaModuleIsDeprecated() {
    VirtualFile firstModuleInfo = java9.get().moduleInfo("module first {requires second;}", ModuleDescriptor.MAIN);
    VirtualFile secondModuleInfo = java9.get().moduleInfo("@Deprecated module second {}", ModuleDescriptor.M2);
    testJavaModule("@Deprecated module <warning descr=\"Deprecated member 'second' is still used\">second</warning> {}",
                   firstModuleInfo, secondModuleInfo);
  }

  @Test
  public void testJavaModuleDependantIsDeprecatedToo() {
    VirtualFile firstModuleInfo = java9.get().moduleInfo("@Deprecated module first {requires second;}", ModuleDescriptor.MAIN);
    VirtualFile secondModuleInfo = java9.get().moduleInfo("@Deprecated module second {}", ModuleDescriptor.M2);
    testJavaModule("@Deprecated module second {}", firstModuleInfo, secondModuleInfo);
  }

  private void testJavaModule(@NotNull String expectedResult, @NotNull VirtualFile firstModuleInfo, @NotNull VirtualFile secondModuleInfo) {
    LightJavaCodeInsightTestFixture myFixture = java9.get().getFixture();
    myFixture.allowTreeAccessForFile(firstModuleInfo);
    myFixture.allowTreeAccessForFile(secondModuleInfo);
    myFixture.configureFromExistingVirtualFile(java9.get().addFile("module-info.java", expectedResult, ModuleDescriptor.M2));
    myFixture.checkHighlighting();
  }
}
