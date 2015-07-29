/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class CreateFieldFromUsageTest extends LightQuickFixTestCase {

  public void testAnonymousClass() throws Exception { doSingleTest(); }
  public void testExpectedTypes() throws Exception { doSingleTest(); }
  public void testInterface() throws Exception { doSingleTest(); }
  public void testMultipleTypes() throws Exception { doSingleTest(); }
  public void testMultipleTypes2() throws Exception { doSingleTest(); }
  public void testParametericMethod() throws Exception { doSingleTest(); }
  public void testQualifyInner() throws Exception { doSingleTest(); }
  public void testTypeArgsFormatted() throws Exception { doSingleTest(); }
  public void testInsideStaticInnerClass() throws Exception { doSingleTest(); }
  public void testCreateFromEquals() throws Exception { doSingleTest(); }
  public void testCreateFromEqualsToPrimitiveType() throws Exception { doSingleTest(); }
  public void testInsideInterface() throws Exception { doSingleTest(); }
  public void testWithAlignment() throws Exception {
    final CommonCodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    boolean old = settings.ALIGN_GROUP_FIELD_DECLARATIONS;
    try {
      settings.ALIGN_GROUP_FIELD_DECLARATIONS = true;
      doSingleTest();
    }
    finally {
      settings.ALIGN_GROUP_FIELD_DECLARATIONS = old;
    }
  }

  public void testSortByRelevance() throws Exception {
    new WriteCommandAction(getProject()) {
      @Override
      protected void run(@NotNull Result result) throws Exception {
        VirtualFile foo = getSourceRoot().createChildDirectory(this, "foo").createChildData(this, "Foo.java");
        VfsUtil.saveText(foo, "package foo; public class Foo { public void put(Object key, Object value) {} }");
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      }
    }.execute();

    doSingleTest();
  }

  public void testDependantConstant() throws Exception {
    doSingleTest();
  }

  public void testArrayBraces() throws Exception {
    doSingleTest();
  }

  protected void doSingleTest() {
    doSingleTest(getTestName(false) + ".java");
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/createFieldFromUsage";
  }

}
