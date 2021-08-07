package com.intellij.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.Property;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;

import java.util.Arrays;

/**
 * @author Dmitry AJvdeev
 */
public class ShowImplementationsTest extends JavaCodeInsightFixtureTestCase {
  
  public void testMessages() {
    myFixture.configureByFiles("Bundles.java", "bundle.properties", "bundle_fr.properties");
    PsiElement[] elements = ShowImplementationsTestUtil.getImplementations();
    assertNotNull(elements);
    assertEquals(2, elements.length);
    assertTrue(elements[0] instanceof IProperty && elements[1] instanceof IProperty);
  }

  public void testMessagesCompletion() {
    myFixture.configureByFiles("Bundles.java", "bundle.properties", "bundle_fr.properties");
    LookupElement[] elements = myFixture.completeBasic();
    assertNotNull(elements);
    assertNotNull(ContainerUtil.find(elements, element -> element.getObject() instanceof Property));
    PsiElement[] implementations = ShowImplementationsTestUtil.getImplementations();
    assertNotNull(implementations);
    assertEquals(Arrays.asList(implementations).toString(), 1, implementations.length);
    assertTrue(implementations[0] instanceof IProperty);
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/showImplementations/";
  }
}
