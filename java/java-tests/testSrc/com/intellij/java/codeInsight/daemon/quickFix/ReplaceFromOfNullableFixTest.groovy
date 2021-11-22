// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.java.codeInsight.daemon.quickFix

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase5
import com.intellij.codeInspection.dataFlow.DataFlowInspection
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import groovy.transform.CompileStatic
import org.junit.jupiter.api.BeforeEach

@CompileStatic
class ReplaceFromOfNullableFixTest extends LightQuickFixParameterizedTestCase5 {

  @BeforeEach
  void setupInspections() {
    getFixture().enableInspections(DataFlowInspection.class);
    if (getTestNameRule().getDisplayName().contains("Guava")) {
      addGuavaOptional(getFixture());
    }
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/replaceFromOfNullable"
  }

  static void addGuavaOptional(JavaCodeInsightTestFixture fixture) {
    fixture.addClass("""
package com.google.common.base;
public abstract class Optional<T> {
  public static <T> Optional<T> absent() { }

  public static <T> Optional<T> of(@org.jetbrains.annotations.NotNull T reference) { }

  public static <T> Optional<T> fromNullable(T nullableReference) { }
}
""")
  }

}