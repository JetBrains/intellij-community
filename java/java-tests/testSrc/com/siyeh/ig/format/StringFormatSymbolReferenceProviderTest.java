// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.format;

import com.intellij.model.Symbol;
import com.intellij.model.psi.PsiSymbolReference;
import com.intellij.model.psi.PsiSymbolReferenceService;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.siyeh.ig.format.StringFormatSymbolReferenceProvider.JavaFormatArgumentSymbol;
import org.jetbrains.annotations.NotNull;
import org.junit.platform.commons.util.CollectionUtils;

import java.util.Collection;
import java.util.Map;

public class StringFormatSymbolReferenceProviderTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_LATEST_WITH_LATEST_JDK;
  }

  public void testResolveFormatSpecifiers() {
    myFixture.configureByText("Test.java", """
      class Demo {
        static void process(String s, Object date, boolean b) {
          String conditional = String.format(b ? "myFormat: num = %1$d, date = %2$s" :
                  "<caret>myFormat: date = %2$s; num = %1$d", 123, date);
        }
      }""");
    PsiLiteralExpression str =
      PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset()),
                                  PsiLiteralExpression.class);
    assertNotNull(str);
    Collection<? extends @NotNull PsiSymbolReference> refs = PsiSymbolReferenceService.getService().getReferences(str);
    assertEquals(2, refs.size());
    Map<String, String> expected = Map.of("%2$s", "date", "%1$d", "123");
    for (PsiSymbolReference ref : refs) {
      assertEquals(str, ref.getElement());
      String formatSpecifier = ref.getRangeInElement().substring(ref.getElement().getText());
      Collection<? extends Symbol> symbols = ref.resolveReference();
      assertEquals(1, symbols.size());
      Symbol symbol = CollectionUtils.getOnlyElement(symbols);
      assertTrue(symbol instanceof JavaFormatArgumentSymbol);
      JavaFormatArgumentSymbol formatSymbol = (JavaFormatArgumentSymbol)symbol;
      assertTrue(formatSymbol.getFormatString() instanceof PsiConditionalExpression);
      String expressionText = formatSymbol.getExpression().getText();
      assertEquals(expected.get(formatSpecifier), expressionText);
    }
  }
}