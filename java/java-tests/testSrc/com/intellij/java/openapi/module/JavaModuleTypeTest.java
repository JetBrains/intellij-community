// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.openapi.module;

import com.intellij.ide.util.projectWizard.JavaModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.TestDisposable;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static com.intellij.testFramework.JavaCodeInsightFixtureKt.setUpJdk;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.moduleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The module type manager owns the one Java module type, and every way to ask for it must return that object.
 * <p>
 * Each assertion compares against the type that the manager registered, and not against another accessor. Two
 * accessors that share an implementation would agree even when the registration is broken.
 */
@TestApplication
public class JavaModuleTypeTest {
  private static final TestFixture<Project> projectFixture = projectFixture();
  private final TestFixture<Path> pathFixture = tempPathFixture();
  private final TestFixture<Module> moduleFixture = moduleFixture(projectFixture, pathFixture, true,
                                                                  JavaModuleType.JAVA_MODULE_ENTITY_TYPE_ID_NAME);

  private static ModuleType<?> registeredType() {
    return ModuleTypeManager.getInstance().getDefaultModuleType();
  }

  @Test
  public void theManagerRegistersTheJavaTypeAsItsDefault() {
    assertInstanceOf(JavaModuleType.class, registeredType());
    assertEquals(JavaModuleType.JAVA_MODULE_ENTITY_TYPE_ID_NAME, registeredType().getId());
  }

  @Test
  public void theManagerFindsTheRegisteredTypeByEveryKnownId() {
    ModuleTypeManager manager = ModuleTypeManager.getInstance();

    assertSame(registeredType(), manager.findByID(JavaModuleType.JAVA_MODULE_ENTITY_TYPE_ID_NAME));
    // "JAVA" is the previous ID of a Java module
    assertSame(registeredType(), manager.findByID("JAVA"));
  }

  @Test
  public void everyAccessorReturnsTheRegisteredType() {
    assertSame(registeredType(), JavaModuleType.getModuleType());
    assertSame(registeredType(), new JavaModuleBuilder().getModuleType());
  }

  @Test
  public void theTypeAndItsBuilderAgree() {
    ModuleBuilder builder = JavaModuleType.getModuleType().createModuleBuilder();

    assertInstanceOf(JavaModuleBuilder.class, builder);
    assertSame(registeredType(), builder.getModuleType());
  }

  @Test
  public void validSdkDumbMode(@TestDisposable Disposable disposable) {
    Project project = projectFixture.get();
    Module module = moduleFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> setUpJdk(LanguageLevel.HIGHEST, project, module, disposable));

    DumbModeTestUtils.runInDumbModeSynchronously(project, () -> {
      assertTrue(DumbService.isDumb(project));
      assertTrue(JavaModuleType.isValidJavaSdk(module));
    });
  }

  @Test
  public void validSdkSmartMode(@TestDisposable Disposable disposable) {
    Project project = projectFixture.get();
    Module module = moduleFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> setUpJdk(LanguageLevel.HIGHEST, project, module, disposable));

    assertFalse(DumbService.isDumb(project));
    assertTrue(JavaModuleType.isValidJavaSdk(module));
  }
}
