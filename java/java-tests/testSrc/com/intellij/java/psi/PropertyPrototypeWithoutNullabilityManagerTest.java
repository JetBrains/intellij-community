// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * JetBrains Light loads the Java PSI without the Java backend, so the project has no {@link NullableNotNullManager}.
 * The field annotation has the TYPE_USE target and one more target, so the prototype code asks the manager about it.
 */
@TestApplication
public final class PropertyPrototypeWithoutNullabilityManagerTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  private static final String FILE_TEXT = """
    import java.lang.annotation.ElementType;
    import java.lang.annotation.Target;

    @Target({ElementType.TYPE_USE, ElementType.FIELD})
    @interface Tag {}

    class Bean {
      @Tag String name;
    }
    """;

  @Test
  public void testGetterPrototype() {
    PsiField field = configureField();
    ReadAction.runBlocking(() -> {
      PsiMethod getter = withoutManager(() -> PropertyUtilBase.generateGetterPrototype(field));
      assertEquals("getName", getter.getName());
    });
  }

  @Test
  public void testSetterPrototype() {
    PsiField field = configureField();
    ReadAction.runBlocking(() -> {
      PsiMethod setter = withoutManager(() -> PropertyUtilBase.generateSetterPrototype(field));
      assertEquals("setName", setter.getName());
    });
  }

  private PsiField configureField() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiJavaFile file = (PsiJavaFile)myFixture.configureByText("Bean.java", FILE_TEXT);
    return ReadAction.computeBlocking(() -> {
      PsiClass bean = file.getClasses()[1];
      return bean.findFieldByName("name", false);
    });
  }

  private <T> T withoutManager(Supplier<T> computation) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    Project project = myFixture.getProject();
    NullableNotNullManager manager = NullableNotNullManager.getInstance(project);
    ServiceContainerUtil.unregisterService(project, NullableNotNullManager.class);
    try {
      assertNull(NullableNotNullManager.getInstance(project));
      return computation.get();
    }
    finally {
      ServiceContainerUtil.registerServiceInstance(project, NullableNotNullManager.class, manager);
    }
  }
}
