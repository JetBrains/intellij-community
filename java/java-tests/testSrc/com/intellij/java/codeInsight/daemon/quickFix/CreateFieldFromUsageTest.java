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
package com.intellij.java.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.quickFix.ActionHint;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class CreateFieldFromUsageTest extends LightQuickFixTestCase {

  public void testAnonymousClass() { doSingleTest(); }
  public void testExpectedTypes() { doSingleTest(); }
  public void testInterface() { doSingleTest(); }
  public void testInterfaceField() { doSingleTest(); }
  public void testSuperInterfaceConstant() { doSingleTest(); }
  public void testMultipleTypes() { doSingleTest(); }
  public void testMultipleTypes2() { doSingleTest(); }
  public void testParametericMethod() { doSingleTest(); }
  public void testQualifyInner() { doSingleTest(); }
  public void testTypeArgsFormatted() { doSingleTest(); }
  public void testInsideStaticInnerClass() { doSingleTest(); }
  public void testCreateFromEquals() { doSingleTest(); }
  public void testCreateFromEqualsToPrimitiveType() { doSingleTest(); }
  public void testInsideInterface() { doSingleTest(); }
  public void testWithAlignment() {
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

  public void testSortByRelevance() {
    new WriteCommandAction(getProject()) {
      @Override
      protected void run(@NotNull Result result) throws Exception {
        VirtualFile foo = getSourceRoot().createChildDirectory(this, "foo").createChildData(this, "Foo.java");
        VfsUtil.saveText(foo, "package foo; public class Foo { public void put(Object key, Object value) {} }");
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      }
    }.execute();
    PsiClass aClass = JavaPsiFacade.getInstance(getProject()).findClass("foo.Foo", GlobalSearchScope.allScope(getProject()));
    assertNotNull(aClass);
    doSingleTest();
  }

  public void testDependantConstant() {
    doSingleTest();
  }

  public void testArrayBraces() {
    doSingleTest();
  }

  protected void doSingleTest() {
    doSingleTest(getTestName(false) + ".java");
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/createFieldFromUsage";
  }

  @Override
  protected ActionHint parseActionHintImpl(@NotNull PsiFile file, @NotNull String contents) {
    return ActionHint.parse(file, contents, false);
  }
}
