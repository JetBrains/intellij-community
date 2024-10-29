// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.format;

import com.intellij.model.Symbol;
import com.intellij.model.psi.PsiSymbolReference;
import com.intellij.model.psi.PsiSymbolReferenceService;
import com.intellij.psi.PsiElement;
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
      final class Demo {
        static void process(String s, Object date, boolean b) {
          String conditional = String.format(b ? "myFormat: num = %1$d, date = %2$s" :
                  "<caret>myFormat: date = %2$s; num = %1$d", 123, date);
        }
      }""");
    PsiLiteralExpression str = getLiteral();
    Collection<? extends @NotNull PsiSymbolReference> refs = PsiSymbolReferenceService.getService().getReferences(str);
    assertEquals(2, refs.size());
    checkRefs(refs, str, str.getParent(), Map.of("%2$s", "date", "%1$d", "123"));
  }

  public void testResolveFromLocalVar() {
    myFixture.configureByText("Test.java", """
      final class Demo {
        static void process(String s) {
          String template = "<caret>Hello %d %s";
          System.out.printf(template, 123, s);
        }
      }""");
    PsiLiteralExpression str = getLiteral();
    Collection<? extends @NotNull PsiSymbolReference> refs = PsiSymbolReferenceService.getService().getReferences(str);
    assertEquals(2, refs.size());
    Map<String, String> expected = Map.of("%d", "123", "%s", "s");
    checkRefs(refs, str, str, expected);
  }

  public void testMessageFormat() {
    myFixture.configureByText("Test.java", """
      import java.text.MessageFormat;
      
      final class Demo {
        static void process(String s) {
          String template = "<caret>Hello {1} {1} {0}";
          System.out.println(MessageFormat.format(template, s, 123));
        }
      }""");
    PsiLiteralExpression str = getLiteral();
    Collection<? extends @NotNull PsiSymbolReference> refs = PsiSymbolReferenceService.getService().getReferences(str);
    assertEquals(3, refs.size());
    Map<String, String> expected = Map.of("{0}", "s", "{1}", "123");
    checkRefs(refs, str, str, expected);
  }
  public void testMessageFormatWithStyle() {
    myFixture.configureByText("Test.java", """
      import java.text.MessageFormat;
      
      final class Demo {
        static void process(String s) {
          String pattern = "<caret>{0} choice: {0,   choice,-1#'''' - 2 quotes|0<more|2<''1{1}'' '''' - 1 quote|3≤{1, number, '#'.00}}";
          System.out.println(MessageFormat.format(pattern, 1, 123));
        }
      }""");
    PsiLiteralExpression str = getLiteral();
    Collection<? extends @NotNull PsiSymbolReference> refs = PsiSymbolReferenceService.getService().getReferences(str);
    assertEquals(3, refs.size());
    Map<String, String> expected = Map.of("{0}", "1", "{0,", "1", "{1,", "123");
    checkRefs(refs, str, str, expected);
  }

  private @NotNull PsiLiteralExpression getLiteral() {
    PsiLiteralExpression str =
      PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset()),
                                  PsiLiteralExpression.class);
    assertNotNull(str);
    return str;
  }

  private static @NotNull JavaFormatArgumentSymbol resolveRef(@NotNull PsiSymbolReference ref) {
    Collection<? extends Symbol> symbols = ref.resolveReference();
    assertEquals(1, symbols.size());
    Symbol symbol = CollectionUtils.getOnlyElement(symbols);
    assertTrue(symbol instanceof JavaFormatArgumentSymbol);
    return (JavaFormatArgumentSymbol)symbol;
  }

  private static void checkRefs(@NotNull Collection<? extends PsiSymbolReference> refs,
                                @NotNull PsiLiteralExpression str,
                                @NotNull PsiElement formatString,
                                @NotNull Map<String, String> expected) {
    for (PsiSymbolReference ref : refs) {
      assertEquals(str, ref.getElement());
      String formatSpecifier = ref.getRangeInElement().substring(ref.getElement().getText());
      JavaFormatArgumentSymbol formatSymbol = resolveRef(ref);
      assertEquals(formatString, formatSymbol.getFormatString());
      String expressionText = formatSymbol.getExpression().getText();
      assertEquals(expected.get(formatSpecifier), expressionText);
    }
  }
}