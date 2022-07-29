// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;

/**
 * @author Konstantin Bulenkov
 */
public class JavaIntroduceVariableTest extends LightJavaCodeInsightTestCase {
  public void testIntroduceBasedOnLiterals() throws Exception {
    doTest("getA(\"simple\")", "simple");
    doTest("getA(\"SimpleName\")", "simpleName", "name");
    doTest("getA(\"simpleName\")", "simpleName", "name");
    doTest("getA(\"simpleClass\")", "simpleClass", "aClass");
    doTest("getA(\"simple_class\")", "simpleClass", "simple_class", "aClass");
    doTest("getA(\"short\")", "aShort");
    doTest("getA(\"boolean\")", "aBoolean");
    doTest("getA().getB(1, \"name\")", "name");
    doTest("getA(\"NAME\")", "name");
    doTest("getA(\"name\")", VariableKind.STATIC_FINAL_FIELD, "NAME");
    doTest("getA(\"SimpleName\")", VariableKind.STATIC_FINAL_FIELD, "SIMPLE_NAME");
    doTest("get(getB().getA(\"SimpleName\").getC())", "simpleName", "name");
  }

  protected void doTest(String expression, VariableKind kind, PsiType type, String... results) throws Exception {
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(getProject());
    final PsiExpression expr = factory.createExpressionFromText(expression, null);
    final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(getProject());
    final SuggestedNameInfo info = codeStyleManager.suggestVariableName(kind, null, expr, type);
    assert info.names.length >= results.length : msg("Can't find some variants", info.names, results);
    for (int i = 0; i < results.length; i++) {
      if (!results[i].equals(info.names[i])) {
        throw new Exception(msg("", info.names, results));
      }
    }
  }

  private static String msg(String s, String[] names, String[] results) {
    return s + ". Expected at first positions: [" + StringUtil.join(results, ",") + "] Found: [" + StringUtil.join(names, ",") + "]";
  }

  protected void doTest(String expression, String... results) throws Exception {
    doTest(expression, VariableKind.LOCAL_VARIABLE, results);
  }

  protected void doTest(String expression, VariableKind kind, String... results) throws Exception {
    doTest(expression, kind, PsiType.getJavaLangString(getPsiManager(), GlobalSearchScope.allScope(getProject())), results);
  }
}
