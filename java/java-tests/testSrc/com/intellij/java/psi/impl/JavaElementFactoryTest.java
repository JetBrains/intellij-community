// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.impl;

import com.intellij.openapi.application.ReadAction;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.IncorrectOperationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class JavaElementFactoryTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  private PsiElementFactory myFactory;

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFactory = JavaPsiFacade.getInstance(myFixture.getProject()).getElementFactory();
  }

  @AfterEach
  void tearDown() {
    myFactory = null;
  }

  @Test
  public void testDocCommentTrimming() {
    ReadAction.runBlocking(() -> myFactory.createDocCommentFromText(" /** ... */"));
  }

  @Test
  public void testDocCommentEmpty() {
    try {
      ReadAction.runBlocking(() -> myFactory.createDocCommentFromText(""));
      fail();
    }
    catch (IncorrectOperationException ignored) { }
  }

  @Test
  public void testArrayClassLanguageLevel() {
    for (LanguageLevel level : LanguageLevel.getEntries()) {
      ReadAction.runBlocking(() -> assertEquals(level, PsiUtil.getLanguageLevel(myFactory.getArrayClass(level))));
    }
  }
}