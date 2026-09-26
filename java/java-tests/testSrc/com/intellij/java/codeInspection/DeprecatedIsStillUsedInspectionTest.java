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
import com.intellij.java.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase;
import com.intellij.java.testFramework.fixtures.MultiModuleJava9ProjectDescriptor.ModuleDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class DeprecatedIsStillUsedInspectionTest extends LightJava9ModulesCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/deprecatedIsStillUsed";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new DeprecatedIsStillUsedInspection());
  }

  public void testSimple() {
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testOverloadedAndStaticImport() {
    myFixture.addClass("""
                         package bar;
                         import static foo.OverloadedAndStaticImport.bar;
                         class Bar {
                           {
                             bar();
                           }
                         }""");
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testDeprecatedWithManyOccurrences() {
    for (int i = 0; i < 20; i++) {
      myFixture.addClass("class MyTest" + i + "{ DeprecatedWithManyOccurrences d;}");
    }
    myFixture.testFile(getTestName(false) + ".java").checkSymbolNames().test();
  }

  public void testJavaModuleIsDeprecated() {
    VirtualFile firstModuleInfo = moduleInfo("module first {requires second;}", ModuleDescriptor.MAIN);
    VirtualFile secondModuleInfo = moduleInfo("@Deprecated module second {}", ModuleDescriptor.M2);
    testJavaModule("@Deprecated module <warning descr=\"Deprecated member 'second' is still used\">second</warning> {}",
                   firstModuleInfo, secondModuleInfo);
  }

  public void testJavaModuleDependantIsDeprecatedToo() {
    VirtualFile firstModuleInfo = moduleInfo("@Deprecated module first {requires second;}", ModuleDescriptor.MAIN);
    VirtualFile secondModuleInfo = moduleInfo("@Deprecated module second {}", ModuleDescriptor.M2);
    testJavaModule("@Deprecated module second {}", firstModuleInfo, secondModuleInfo);
  }

  private void testJavaModule(@NotNull String expectedResult, @NotNull VirtualFile firstModuleInfo, @NotNull VirtualFile secondModuleInfo) {
    myFixture.allowTreeAccessForFile(firstModuleInfo);
    myFixture.allowTreeAccessForFile(secondModuleInfo);
    myFixture.configureFromExistingVirtualFile(addFile("module-info.java", expectedResult, ModuleDescriptor.M2));
    myFixture.checkHighlighting();
  }
}
