// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import java.util.function.Supplier;

/**
 * JetBrains Light loads the Java PSI without the Java backend, so the project has no {@link NullableNotNullManager}.
 * The field annotation has the TYPE_USE target and one more target, so the prototype code asks the manager about it.
 */
public final class PropertyPrototypeWithoutNullabilityManagerTest extends LightJavaCodeInsightFixtureTestCase {
  private static final String FILE_TEXT = """
    import java.lang.annotation.ElementType;
    import java.lang.annotation.Target;

    @Target({ElementType.TYPE_USE, ElementType.FIELD})
    @interface Tag {}

    class Bean {
      @Tag String name;
    }
    """;

  public void testGetterPrototype() {
    PsiField field = configureField();
    PsiMethod getter = withoutManager(() -> PropertyUtilBase.generateGetterPrototype(field));
    assertEquals("getName", getter.getName());
  }

  public void testSetterPrototype() {
    PsiField field = configureField();
    PsiMethod setter = withoutManager(() -> PropertyUtilBase.generateSetterPrototype(field));
    assertEquals("setName", setter.getName());
  }

  private PsiField configureField() {
    PsiJavaFile file = (PsiJavaFile)myFixture.configureByText("Bean.java", FILE_TEXT);
    PsiClass bean = file.getClasses()[1];
    return bean.findFieldByName("name", false);
  }

  private <T> T withoutManager(Supplier<T> computation) {
    Project project = getProject();
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
