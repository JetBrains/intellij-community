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
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.MultiFileTestCase;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class RenameClassTest extends MultiFileTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testNonJava() {
    doTest("pack1.Class1", "Class1New");
  }

  public void testCollision() {
    doTest("pack1.MyList", "List");
  }

  public void testPackageClassConflict() {
    doTest("Fabric.AFabric", "MetaFabric");
  }

  public void testInnerClass() {
    doTest("pack1.OuterClass.InnerClass", "NewInnerClass");
  }

  public void testImport() {
    //noinspection SpellCheckingInspection
    doTest("a.Blubfoo", "BlubFoo");
  }

  public void testInSameFile() {
    doTest("Two", "Object");
  }
  
  public void testConstructorJavadoc() {
    doTest("Test", "Test1");
  }

  public void testCollision1() {
    doTest("Loader", "Reader");
  }

  public void testImplicitReferenceToDefaultCtr() {
    doTest("pack1.Parent", "ParentXXX");
  }

  public void testImplicitlyImported() {
    doTest("pack1.A", "Object");
  }

  public void testAutomaticRenameVars() {
    doRenameClass("XX", "Y");
  }

  public void testAutomaticRenameLambdaParams() {
    doRenameClass("Bar", "Baz");
  }

  private void doRenameClass(final String className, final String newName) {
    doTest((rootDir, rootAfter) -> {
      PsiClass aClass = myJavaFacade.findClass(className, GlobalSearchScope.allScope(getProject()));
      assertNotNull("Class XX not found", aClass);

      final RenameProcessor processor = new RenameProcessor(myProject, aClass, newName, true, true);
      for (AutomaticRenamerFactory factory : Extensions.getExtensions(AutomaticRenamerFactory.EP_NAME)) {
        processor.addRenamerFactory(factory);
      }
      processor.run();
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();
      FileDocumentManager.getInstance().saveAllDocuments();
    });
  }

  public void testAutomaticRenameInheritors() {
    doRenameClass("MyClass", "MyClass1");
  }

  public void testAutomaticRenameVarsCollision() {
    doTest("XX", "Y");
  }

  private void doTest(@NonNls final String qClassName, @NonNls final String newName) {
    doTest((rootDir, rootAfter) -> this.performAction(qClassName, newName));
  }

  private void performAction(String qClassName, String newName) {
    PsiClass aClass = myJavaFacade.findClass(qClassName, GlobalSearchScope.allScope(getProject()));
    assertNotNull("Class " + qClassName + " not found", aClass);

    new RenameProcessor(myProject, aClass, newName, true, true).run();
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    FileDocumentManager.getInstance().saveAllDocuments();
  }

  @NotNull
  @Override
  protected String getTestRoot() {
    return "/refactoring/renameClass/";
  }

  @Override
  protected Sdk getTestProjectJdk() {
    return IdeaTestUtil.getMockJdk18();
  }
}
