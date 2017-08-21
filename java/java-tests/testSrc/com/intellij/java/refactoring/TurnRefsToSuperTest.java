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

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.MultiFileTestCase;
import com.intellij.refactoring.turnRefsToSuper.TurnRefsToSuperProcessor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class TurnRefsToSuperTest extends MultiFileTestCase {
  public void testSuperClass() { doTest("AClass", "ASuper", true); }
  public void testMethodFromSuper() { doTest("AClass", "ASuper", true); }
  public void testRemoveImport() { doTest("pack1.AClass", "pack1.AnInterface", true); }
  public void testToArray() { doTest("A", "I", true); }
  public void testArrayElementAssignment() { doTest("C", "I", true); }
  public void testReturnValue() { doTest("A", "I", true); }
  public void testReturnValue2() { doTest("A", "I", true); }
  public void testCast() { doTest("A", "I", true); }
  public void testUseAsArg() { doTest("AClass", "I", true); }
  public void testClassUsage() { doTest("A", "I", true); }
  public void testInstanceOf() { doTest("A", "I", false); }
  public void testFieldTest() { doTest("Component1", "IDoSomething", false); }
  public void testScr34000() { doTest("SimpleModel", "Model", false); }
  public void testScr34020() { doTest(CommonClassNames.JAVA_UTIL_LIST, CommonClassNames.JAVA_UTIL_COLLECTION, false); }
  public void testCommonInheritor() { doTest("Client.V", "Client.L", false); }
  public void testCommonInheritorFail() { doTest("Client.V", "Client.L", false); }
  public void testCommonInheritorResults() { doTest("Client.V", "Client.L", false); }
  public void testCommonInheritorResultsFail() { doTest("Client.V", "Client.L", false); }
  public void testCommonInheritorResultsFail2() { doTest("Client.V", "Client.L", false); }
  public void testIDEA6505() { doTest("Impl", "IB", false); }
  public void testIDEADEV5517() { doTest("Xyz", "XInt", false); }
  public void testIDEADEV5517NOOP() { doTest("Xyz", "XInt", false); }
  public void testIDEADEV6136() { doTest("A", "B", false); }
  public void testIDEADEV25669() { doTest("p.A", "p.Base", false); }
  public void testIDEADEV23807() { doTest("B", "A", false); }
  public void testTypeArgumentsRH() { doTest("IImpl", "I", false); }
  public void testTypeArgumentsRH1() { doTest("IImpl", "I", false); }
  public void testAnonymousWithTypeArguments() { doTest("Clazz", "IntF", false); }
  public void testTypeArgumentsParam() { doTest("Clazz", "IntF", false); }
  public void testTryWithResources1() { doTest("Test.MyResourceImpl", "Test.MyResource", false); }
  public void testTryWithResources2() { doTest("Test.MyResourceImpl", "Test.MyResource", false); }
  public void testDifferentNumberOfParams() { doTest("Bar", "SuperBar", false); }

  //todo[ann] fix and uncomment
  //public void testStaticCallArguments() throws Exception { doTest("Impl", "Int", false); }
  //public void testListArgs() throws Exception { doTest("Impl", "Int", false); }
  //public void testCovariantReturnTypes() throws Exception { doTest("Impl", "Int", false); }
  //public void testNewExpr() throws Exception { doTest("Impl", "Int", false); }
  //public void testForEach1() throws Exception { doTest("Test.MyIterableImpl", "Test.MyIterable", false); }
  //public void testForEach2() throws Exception { doTest("Test.MyIterableImpl", "Test.MyIterable", false); }

  private void doTest(@NonNls final String className, @NonNls final String superClassName, final boolean replaceInstanceOf) {
    doTest((rootDir, rootAfter) -> this.performAction(className, superClassName, replaceInstanceOf), true);
  }

  @NotNull
  @Override
  public String getTestRoot() {
    return "/refactoring/turnRefsToSuper/";
  }

  private void performAction(final String className, final String superClassName, boolean replaceInstanceOf) {
    final PsiClass aClass = myJavaFacade.findClass(className, GlobalSearchScope.allScope(myProject));
    assertNotNull("Class " + className + " not found", aClass);
    PsiClass superClass = myJavaFacade.findClass(superClassName, GlobalSearchScope.allScope(myProject));
    assertNotNull("Class " + superClassName + " not found", superClass);

    new TurnRefsToSuperProcessor(myProject, aClass, superClass, replaceInstanceOf).run();
    FileDocumentManager.getInstance().saveAllDocuments();
  }
}
