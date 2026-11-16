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
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.refactoring.LightMultiFileFixture;
import com.intellij.refactoring.turnRefsToSuper.TurnRefsToSuperProcessor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NonNls;
import org.junit.jupiter.api.Test;

import static com.intellij.refactoring.LightMultiFileFixtureKt.lightMultiFileFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class TurnRefsToSuperTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightMultiFileFixture> multiFile =
    lightMultiFileFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/refactoring/turnRefsToSuper/");

  @Test
  public void testSuperClass() { doTest("AClass", "ASuper", true); }
  @Test
  public void testMethodFromSuper() { doTest("AClass", "ASuper", true); }
  @Test
  public void testRemoveImport() { doTest("pack1.AClass", "pack1.AnInterface", true); }
  @Test
  public void testToArray() { doTest("A", "I", true); }
  @Test
  public void testArrayElementAssignment() { doTest("C", "I", true); }
  @Test
  public void testReturnValue() { doTest("A", "I", true); }
  @Test
  public void testReturnValue2() { doTest("A", "I", true); }
  @Test
  public void testCast() { doTest("A", "I", true); }
  @Test
  public void testUseAsArg() { doTest("AClass", "I", true); }
  @Test
  public void testClassUsage() { doTest("A", "I", true); }
  @Test
  public void testInstanceOf() { doTest("A", "I", false); }
  @Test
  public void testFieldTest() { doTest("Component1", "IDoSomething", false); }
  @Test
  public void testScr34000() { doTest("SimpleModel", "Model", false); }
  @Test
  public void testScr34020() { doTest(CommonClassNames.JAVA_UTIL_LIST, CommonClassNames.JAVA_UTIL_COLLECTION, false); }
  @Test
  public void testCommonInheritor() { doTest("Client.V", "Client.L", false); }
  @Test
  public void testCommonInheritorFail() { doTest("Client.V", "Client.L", false); }
  @Test
  public void testCommonInheritorResults() { doTest("Client.V", "Client.L", false); }
  @Test
  public void testCommonInheritorResultsFail() { doTest("Client.V", "Client.L", false); }
  @Test
  public void testCommonInheritorResultsFail2() { doTest("Client.V", "Client.L", false); }
  @Test
  public void testIDEA6505() { doTest("Impl", "IB", false); }
  @Test
  public void testIDEADEV5517() { doTest("Xyz", "XInt", false); }
  @Test
  public void testIDEADEV5517NOOP() { doTest("Xyz", "XInt", false); }
  @Test
  public void testIDEADEV6136() { doTest("A", "B", false); }
  @Test
  public void testIDEADEV25669() { doTest("p.A", "p.Base", false); }
  @Test
  public void testIDEADEV23807() { doTest("B", "A", false); }
  @Test
  public void testTypeArgumentsRH() { doTest("IImpl", "I", false); }
  @Test
  public void testTypeArgumentsRH1() { doTest("IImpl", "I", false); }
  @Test
  public void testAnonymousWithTypeArguments() { doTest("Clazz", "IntF", false); }
  @Test
  public void testTypeArgumentsParam() { doTest("Clazz", "IntF", false); }
  @Test
  public void testTryWithResources1() { doTest("Test.MyResourceImpl", "Test.MyResource", false); }
  @Test
  public void testTryWithResources2() { doTest("Test.MyResourceImpl", "Test.MyResource", false); }
  @Test
  public void testDifferentNumberOfParams() { doTest("Bar", "SuperBar", false); }
  @Test
  public void testSelfReference() { doTest("Bar", "SuperBar", false); }

  //todo[ann] fix and uncomment
  //public void testStaticCallArguments() throws Exception { doTest("Impl", "Int", false); }
  //public void testListArgs() throws Exception { doTest("Impl", "Int", false); }
  //public void testCovariantReturnTypes() throws Exception { doTest("Impl", "Int", false); }
  //public void testNewExpr() throws Exception { doTest("Impl", "Int", false); }
  //public void testForEach1() throws Exception { doTest("Test.MyIterableImpl", "Test.MyIterable", false); }
  //public void testForEach2() throws Exception { doTest("Test.MyIterableImpl", "Test.MyIterable", false); }

  private void doTest(@NonNls final String className, @NonNls final String superClassName, final boolean replaceInstanceOf) {
    multiFile.get().doTest(() -> this.performAction(className, superClassName, replaceInstanceOf), true);
  }

  private void performAction(final String className, final String superClassName, boolean replaceInstanceOf) {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    final PsiClass aClass = myFixture.findClass(className);
    assertNotNull(aClass, "Class " + className + " not found");
    PsiClass superClass = myFixture.findClass(superClassName);
    assertNotNull(superClass, "Class " + superClassName + " not found");

    new TurnRefsToSuperProcessor(myFixture.getProject(), aClass, superClass, replaceInstanceOf).run();
  }
}
