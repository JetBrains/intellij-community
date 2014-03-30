/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.codeInspection.i18n.I18nQuickFixHandler;
import com.intellij.codeInspection.i18n.I18nizeAction;
import com.intellij.codeInspection.i18n.JavaI18nUtil;
import com.intellij.ide.DataManager;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.Collections;


public class I18nizeTest extends LightCodeInsightTestCase {
  @NonNls
  private static String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/i18nize";
  }

  private void doTest(@NonNls String ext) throws Exception {
    configureByFile(getBasePath() + "/before"+getTestName(false)+"."+ext);
    I18nizeAction action = new I18nizeAction();
    DataContext dataContext = DataManager.getInstance().getDataContext(myEditor.getComponent());
    AnActionEvent event = new AnActionEvent(null, dataContext, "place", action.getTemplatePresentation(), null, 0);
    action.update(event);
    @NonNls String afterFile = getBasePath() + "/after" + getTestName(false) + "." + ext;
    boolean afterFileExists = new File(PathManagerEx.getTestDataPath() + afterFile).exists();
    I18nQuickFixHandler handler = I18nizeAction.getHandler(event);
    try {
      if (handler != null) {
        handler.checkApplicability(getFile(), getEditor());
      }
    }
    catch (IncorrectOperationException e) {
      event.getPresentation().setEnabled(false);
    }
    assertEquals(afterFileExists, event.getPresentation().isEnabled());

    if (afterFileExists) {
      PsiLiteralExpression literalExpression = I18nizeAction.getEnclosingStringLiteral(getFile(), getEditor());
      assertNotNull(handler);
      handler.performI18nization(getFile(), getEditor(), literalExpression, Collections.<PropertiesFile>emptyList(), "key1", "value1", "i18nizedExpr",
                                 PsiExpression.EMPTY_ARRAY, JavaI18nUtil.DEFAULT_PROPERTY_CREATION_HANDLER);
      checkResultByFile(afterFile);
    }
  }

  public void testLiteral() throws Exception {doTest("java");}
  public void testOutsideLiteral() throws Exception {doTest("java");}
  public void testLiteralRightSubSelection() throws Exception {doTest("java");}
  public void testCaretAtPlus() throws Exception {doTest("java");}

  public void testLongConcat() throws Exception {doTest("java");}
  public void testCharacterLiteral() throws Exception {doTest("java");}
}
