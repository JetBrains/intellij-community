// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module;

import com.intellij.ide.util.projectWizard.JavaModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.testFramework.junit5.TestApplication;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The module type manager owns the one Java module type, and every way to ask for it must return that object.
 * <p>
 * Each assertion compares against the type that the manager registered, and not against another accessor. Two
 * accessors that share an implementation would agree even when the registration is broken.
 */
@TestApplication
public class JavaModuleTypeTest {
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
}
