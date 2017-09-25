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

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.RedundantSuppressInspection;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.emptyMethod.EmptyMethodInspection;
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.i18n.I18nInspection;
import com.intellij.codeInspection.javaDoc.JavaDocReferenceInspection;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.injected.MyTestInjector;
import com.intellij.testFramework.InspectionTestCase;
import com.siyeh.ig.migration.RawUseOfParameterizedTypeInspection;
import org.jetbrains.annotations.NotNull;

public class RedundantSuppressTest extends InspectionTestCase {
  private GlobalInspectionToolWrapper myWrapper;
  private InspectionToolWrapper[] myInspectionToolWrappers;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myInspectionToolWrappers = new InspectionToolWrapper[]{
      new LocalInspectionToolWrapper(new JavaDocReferenceInspection()),
      new LocalInspectionToolWrapper(new I18nInspection()),
      new LocalInspectionToolWrapper(new RawUseOfParameterizedTypeInspection()),
      new GlobalInspectionToolWrapper(new EmptyMethodInspection()),
      new GlobalInspectionToolWrapper(new UnusedDeclarationInspection())};

    myWrapper = new GlobalInspectionToolWrapper(new RedundantSuppressInspection() {
      @NotNull
      @Override
      protected InspectionToolWrapper[] getInspectionTools(PsiElement psiElement, @NotNull InspectionManager manager) {
        return myInspectionToolWrappers;
      }
    });
  }

  @Override
  protected void tearDown() throws Exception {
    myWrapper = null;
    myInspectionToolWrappers = null;
    super.tearDown();
  }

  public void testModuleInfo() {
    doTest();
  }

  public void testDefaultFile() {
    doTest();
  }

  public void testAlternativeIds() {
    doTest();
  }

  public void testIgnoreUnused() {
    doTest();
  }

  public void testSuppressAll() {
    try {
      ((RedundantSuppressInspection)myWrapper.getTool()).IGNORE_ALL = true;
      doTest();
    }
    finally {
      ((RedundantSuppressInspection)myWrapper.getTool()).IGNORE_ALL = false;
    }
  }

  public void testInjections() {
    MyTestInjector testInjector = new MyTestInjector(getPsiManager());
    testInjector.injectAll(getTestRootDisposable());
    
    doTest();
  }

  private void doTest() {
    doTest("redundantSuppress/" + getTestName(true), myWrapper,"java 1.5",true);
  }
}
