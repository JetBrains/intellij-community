// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.encapsulation;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class AssignmentOrReturnOfFieldWithMutableTypeInspectionTest extends LightJavaInspectionTestCase {

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      """
      package com.google.common.collect;
      
      import java.util.List;
      
      public class ImmutableList<E> implements List<E> {
        public static ImmutableList<?> of() {return new ImmutableList<>();}
        public static <T> ImmutableList<T> copyOf(List<T> list) {return new ImmutableList<>();}
      }"""
    };
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_17;
  }

  public void testAssignmentOrReturnOfFieldWithMutableType() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new AssignmentOrReturnOfFieldWithMutableTypeInspection();
  }
}