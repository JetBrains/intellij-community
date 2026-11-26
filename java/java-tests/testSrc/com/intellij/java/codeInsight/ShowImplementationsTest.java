// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.ShowImplementationsTestUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static com.intellij.testFramework.JavaCodeInsightFixtureKt.javaModuleCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * See also {@link com.intellij.java.navigation.ShowImplementationHandlerTest}
 * @author Dmitry AJvdeev
 */
@TestApplication
public class ShowImplementationsTest {
  private final TestFixture<JavaCodeInsightTestFixture> codeInsightFixture =
    javaModuleCodeInsightFixture(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/showImplementations/");

  @Test
  public void testMessages() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFiles("Bundles.java", "bundle.properties", "bundle_fr.properties");
    PsiElement[] elements = EdtTestUtil.runInEdtAndGet(() -> ShowImplementationsTestUtil.getImplementations());
    assertNotNull(elements);
    assertEquals(2, elements.length);
    assertInstanceOf(IProperty.class, elements[0]);
    assertInstanceOf(IProperty.class, elements[1]);
  }

  @Test
  public void testMessagesCompletion() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFiles("Bundles.java", "bundle.properties", "bundle_fr.properties");
    LookupElement[] elements = myFixture.completeBasic();
    assertNotNull(elements);
    assertNotNull(ContainerUtil.find(elements, element -> element.getObject() instanceof Property));
    PsiElement[] implementations = EdtTestUtil.runInEdtAndGet(() -> ShowImplementationsTestUtil.getImplementations());
    assertNotNull(implementations);
    String message = ReadAction.computeBlocking(() -> Arrays.asList(implementations).toString());
    assertEquals(1, implementations.length, message);
    assertInstanceOf(IProperty.class, implementations[0]);
  }
}
