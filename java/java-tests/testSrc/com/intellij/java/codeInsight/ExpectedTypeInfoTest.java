// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.ModNavigatorTailType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class ExpectedTypeInfoTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData");

  @Test
  public void testIntersectStrictStrict1() {
    ReadAction.runBlocking(() -> {
      ExpectedTypeInfo info1 = createInfo("java.lang.Exception", ExpectedTypeInfo.TYPE_STRICTLY);
      ExpectedTypeInfo info2 = createInfo("java.lang.Exception", ExpectedTypeInfo.TYPE_STRICTLY);

      ExpectedTypeInfo[] result = info1.intersect(info2);
      assertEquals(1, result.length);
      assertEquals(ExpectedTypeInfo.TYPE_STRICTLY, result[0].getKind());
      assertTrue(result[0].getType().equalsToText("java.lang.Exception"));
    });
  }

  @Test
  public void testIntersectStrictStrict2() {
    ReadAction.runBlocking(() -> {
      ExpectedTypeInfo info1 = createInfo("java.lang.Exception", ExpectedTypeInfo.TYPE_STRICTLY);
      ExpectedTypeInfo info2 = createInfo(CommonClassNames.JAVA_LANG_OBJECT, ExpectedTypeInfo.TYPE_STRICTLY);

      ExpectedTypeInfo[] result = info1.intersect(info2);
      assertEquals(0, result.length);
    });
  }

  @Test
  public void testIntersectSubtypeStrict1() {
    ReadAction.runBlocking(() -> {
      ExpectedTypeInfo info1 = createInfo("java.lang.Exception", ExpectedTypeInfo.TYPE_OR_SUBTYPE);
      ExpectedTypeInfo info2 = createInfo("java.io.IOException", ExpectedTypeInfo.TYPE_STRICTLY);

      ExpectedTypeInfo[] result = info1.intersect(info2);
      assertEquals(1, result.length);
      assertEquals(ExpectedTypeInfo.TYPE_STRICTLY, result[0].getKind());
      assertTrue(result[0].getType().equalsToText("java.io.IOException"));
    });
  }

  @Test
  public void testIntersectSubtypeStrict2() {
    ReadAction.runBlocking(() -> {
      ExpectedTypeInfo info1 = createInfo("java.lang.Exception", ExpectedTypeInfo.TYPE_OR_SUBTYPE);
      ExpectedTypeInfo info2 = createInfo(CommonClassNames.JAVA_LANG_OBJECT, ExpectedTypeInfo.TYPE_STRICTLY);

      ExpectedTypeInfo[] result = info1.intersect(info2);
      assertEquals(0, result.length);
    });
  }

  @Test
  public void testIntersectSupertypeStrict1() {
    ReadAction.runBlocking(() -> {
      ExpectedTypeInfo info1 = createInfo("java.io.IOException", ExpectedTypeInfo.TYPE_OR_SUPERTYPE);
      ExpectedTypeInfo info2 = createInfo("java.lang.Exception", ExpectedTypeInfo.TYPE_STRICTLY);

      ExpectedTypeInfo[] result = info1.intersect(info2);
      assertEquals(1, result.length);
      assertEquals(ExpectedTypeInfo.TYPE_STRICTLY, result[0].getKind());
      assertTrue(result[0].getType().equalsToText("java.lang.Exception"));
    });
  }

  @Test
  public void testIntersectSupertypeStrict2() {
    ReadAction.runBlocking(() -> {
      ExpectedTypeInfo info1 = createInfo("java.io.IOException", ExpectedTypeInfo.TYPE_OR_SUPERTYPE);
      ExpectedTypeInfo info2 = createInfo("java.io.EOFException", ExpectedTypeInfo.TYPE_STRICTLY);

      ExpectedTypeInfo[] result = info1.intersect(info2);
      assertEquals(0, result.length);
    });
  }

  @Test
  public void testIntersectStrictSubtype1() {
    ReadAction.runBlocking(() -> {
      ExpectedTypeInfo info1 = createInfo("java.io.IOException", ExpectedTypeInfo.TYPE_STRICTLY);
      ExpectedTypeInfo info2 = createInfo("java.lang.Exception", ExpectedTypeInfo.TYPE_OR_SUBTYPE);

      ExpectedTypeInfo[] result = info1.intersect(info2);
      assertEquals(1, result.length);
      assertEquals(ExpectedTypeInfo.TYPE_STRICTLY, result[0].getKind());
      assertTrue(result[0].getType().equalsToText("java.io.IOException"));
    });
  }

  @Test
  public void testIntersectStrictSubtype2() {
    ReadAction.runBlocking(() -> {
      ExpectedTypeInfo info1 = createInfo(CommonClassNames.JAVA_LANG_OBJECT, ExpectedTypeInfo.TYPE_STRICTLY);
      ExpectedTypeInfo info2 = createInfo("java.lang.Exception", ExpectedTypeInfo.TYPE_OR_SUBTYPE);

      ExpectedTypeInfo[] result = info1.intersect(info2);
      assertEquals(0, result.length);
    });
  }

  @Test
  public void testIntersectStrictSupertype1() {
    ReadAction.runBlocking(() -> {
      ExpectedTypeInfo info1 = createInfo("java.lang.Exception", ExpectedTypeInfo.TYPE_STRICTLY);
      ExpectedTypeInfo info2 = createInfo("java.io.IOException", ExpectedTypeInfo.TYPE_OR_SUPERTYPE);

      ExpectedTypeInfo[] result = info1.intersect(info2);
      assertEquals(1, result.length);
      assertEquals(ExpectedTypeInfo.TYPE_STRICTLY, result[0].getKind());
      assertTrue(result[0].getType().equalsToText("java.lang.Exception"));
    });
  }

  @Test
  public void testIntersectStrictSupertype2() {
    ReadAction.runBlocking(() -> {
      ExpectedTypeInfo info1 = createInfo("java.io.EOFException", ExpectedTypeInfo.TYPE_STRICTLY);
      ExpectedTypeInfo info2 = createInfo("java.io.IOException", ExpectedTypeInfo.TYPE_OR_SUPERTYPE);

      ExpectedTypeInfo[] result = info1.intersect(info2);
      assertEquals(0, result.length);
    });
  }

  @Test
  public void testIntersectSubtypeSubtype1() {
    ReadAction.runBlocking(() -> {
      ExpectedTypeInfo info1 = createInfo("java.lang.Exception", ExpectedTypeInfo.TYPE_OR_SUBTYPE);
      ExpectedTypeInfo info2 = createInfo("java.io.IOException", ExpectedTypeInfo.TYPE_OR_SUBTYPE);

      ExpectedTypeInfo[] result = info1.intersect(info2);
      assertEquals(1, result.length);
      assertEquals(ExpectedTypeInfo.TYPE_OR_SUBTYPE, result[0].getKind());
      assertTrue(result[0].getType().equalsToText("java.io.IOException"));
    });
  }

  @Test
  public void testIntersectSubtypeSubtype2() {
    ReadAction.runBlocking(() -> {
      ExpectedTypeInfo info1 = createInfo("java.io.IOException", ExpectedTypeInfo.TYPE_OR_SUBTYPE);
      ExpectedTypeInfo info2 = createInfo("java.lang.Exception", ExpectedTypeInfo.TYPE_OR_SUBTYPE);

      ExpectedTypeInfo[] result = info1.intersect(info2);
      assertEquals(1, result.length);
      assertEquals(ExpectedTypeInfo.TYPE_OR_SUBTYPE, result[0].getKind());
      assertTrue(result[0].getType().equalsToText("java.io.IOException"));
    });
  }

  @Test
  public void testIntersectSubtypeSubtype3() {
    ReadAction.runBlocking(() -> {
      ExpectedTypeInfo info1 = createInfo("java.io.IOException", ExpectedTypeInfo.TYPE_OR_SUBTYPE);
      ExpectedTypeInfo info2 = createInfo("javax.swing.JButton", ExpectedTypeInfo.TYPE_OR_SUBTYPE);

      ExpectedTypeInfo[] result = info1.intersect(info2);
      assertEquals(0, result.length);
    });
  }

  @Test
  public void testIntersectSuperSuper1() {
    ReadAction.runBlocking(() -> {
      ExpectedTypeInfo info1 = createInfo("java.io.EOFException", ExpectedTypeInfo.TYPE_OR_SUPERTYPE);
      ExpectedTypeInfo info2 = createInfo("java.io.IOException", ExpectedTypeInfo.TYPE_OR_SUPERTYPE);

      ExpectedTypeInfo[] result = info1.intersect(info2);
      assertEquals(1, result.length);
      assertEquals(ExpectedTypeInfo.TYPE_OR_SUPERTYPE, result[0].getKind());
      assertTrue(result[0].getType().equalsToText("java.io.IOException"));
    });
  }

  @Test
  public void testIntersectSuperSuper2() {
    ReadAction.runBlocking(() -> {
      ExpectedTypeInfo info1 = createInfo("java.io.IOException", ExpectedTypeInfo.TYPE_OR_SUPERTYPE);
      ExpectedTypeInfo info2 = createInfo("java.io.EOFException", ExpectedTypeInfo.TYPE_OR_SUPERTYPE);

      ExpectedTypeInfo[] result = info1.intersect(info2);
      assertEquals(1, result.length);
      assertEquals(ExpectedTypeInfo.TYPE_OR_SUPERTYPE, result[0].getKind());
      assertTrue(result[0].getType().equalsToText("java.io.IOException"));
    });
  }

  @Test
  public void testIntersectSuperSuper3() {
    ReadAction.runBlocking(() -> {
      ExpectedTypeInfo info1 = createInfo("java.io.IOException", ExpectedTypeInfo.TYPE_OR_SUPERTYPE);
      ExpectedTypeInfo info2 = createInfo("javax.swing.JButton", ExpectedTypeInfo.TYPE_OR_SUPERTYPE);

      ExpectedTypeInfo[] result = info1.intersect(info2);
      assertEquals(0, result.length);
    });
  }

  @Test
  public void testIntersectSubSuper1() {
    ReadAction.runBlocking(() -> {
      ExpectedTypeInfo info1 = createInfo("java.lang.Exception", ExpectedTypeInfo.TYPE_OR_SUBTYPE);
      ExpectedTypeInfo info2 = createInfo("java.io.EOFException", ExpectedTypeInfo.TYPE_OR_SUPERTYPE);

      ExpectedTypeInfo[] result = info1.intersect(info2);
      assertNotNull(result);
      assertEquals(1, result.length);
      assertEquals(ExpectedTypeInfo.TYPE_OR_SUPERTYPE, result[0].getKind());
      assertTrue(result[0].getType().equalsToText("java.io.EOFException"));
    });
  }

  @NotNull
  private ExpectedTypeInfo createInfo(String className, @ExpectedTypeInfo.Type int kind) {
    Project project = codeInsightFixture.get().getProject();
    PsiType type = JavaPsiFacade.getElementFactory(project).createTypeByFQClassName(className, GlobalSearchScope.allScope(project));
    return ExpectedTypesProvider.createInfo(type, kind, type, ModNavigatorTailType.noneType());
  }
}
