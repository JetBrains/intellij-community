// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

public class DataFlowInspection26Test extends DataFlowInspectionTestCase {

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_26;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/dataFlow/fixture/";
  }

  @SuppressWarnings("Since15")
  public void testLazyConstantBasics() {
    myFixture.addClass("""
                         package java.lang;
                         import java.util.function.Supplier;
                         
                         public final class LazyConstant<V> {
                             public static native <V> LazyConstant<V> of(Supplier<? extends V> supplier);
                         
                             public V get();
                         }
                         """);
    doTestWith((dfi, cvi) -> dfi.TREAT_UNKNOWN_MEMBERS_AS_NULLABLE = false);
  }
}