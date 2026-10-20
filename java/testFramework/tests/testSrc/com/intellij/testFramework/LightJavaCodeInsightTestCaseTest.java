// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class LightJavaCodeInsightTestCaseTest {
  @ClassRule public static final ApplicationRule appRule = new ApplicationRule();

  @Test
  public void differentAnnotationLibrariesHaveDifferentDescriptors() {
    var java7 = descriptor(LanguageLevel.JDK_1_7);
    var java8 = descriptor(LanguageLevel.JDK_1_8);

    assertNotEquals(java7, java8);
    assertNotEquals(java8, java7);
  }

  @Test
  public void declarationAnnotationsAllowProjectReuse() {
    assertEqualDescriptors(LanguageLevel.JDK_1_5, LanguageLevel.JDK_1_7);
  }

  @Test
  public void typeUseAnnotationsAllowProjectReuse() {
    assertEqualDescriptors(LanguageLevel.JDK_1_8, LanguageLevel.HIGHEST);
  }

  private static void assertEqualDescriptors(LanguageLevel first, LanguageLevel second) {
    var firstDescriptor = descriptor(first);
    var secondDescriptor = descriptor(second);

    assertEquals(firstDescriptor, secondDescriptor);
    assertEquals(secondDescriptor, firstDescriptor);
    assertEquals(firstDescriptor.hashCode(), secondDescriptor.hashCode());
  }

  private static LightProjectDescriptor descriptor(LanguageLevel languageLevel) {
    return new LightJavaCodeInsightTestCase() {
      @Override
      public @NotNull LightProjectDescriptor getProjectDescriptor() {
        return super.getProjectDescriptor();
      }

      @Override
      protected LanguageLevel getLanguageLevel() {
        return languageLevel;
      }

      @Override
      protected Sdk getProjectJDK() {
        return null;
      }
    }.getProjectDescriptor();
  }
}
