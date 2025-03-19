// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.dataFlow.ConstantValueInspection;
import com.intellij.codeInspection.dataFlow.DataFlowInspection;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.intellij.lang.annotations.Language;

import java.util.function.BiConsumer;

public abstract class DataFlowInspectionTestCase extends LightJavaCodeInsightFixtureTestCase {
  protected void doTest() {
    doTestWith((df, cv) -> {
      df.SUGGEST_NULLABLE_ANNOTATIONS = true;
      cv.REPORT_CONSTANT_REFERENCE_VALUES = false;
    });
  }

  protected void doTestWith(BiConsumer<DataFlowInspection, ConstantValueInspection> inspectionMutator) {
    DataFlowInspection inspection = new DataFlowInspection();
    ConstantValueInspection cvInspection = new ConstantValueInspection();
    inspectionMutator.accept(inspection, cvInspection);
    myFixture.enableInspections(inspection, cvInspection);
    myFixture.testHighlighting(true, false, true, getTestName(false) + ".java");
  }

  static void addCheckerAnnotations(JavaCodeInsightTestFixture fixture) {
    fixture.addClass("package org.checkerframework.checker.nullness.qual;import java.lang.annotation.*;" +
                     "@Target(ElementType.TYPE_USE)public @interface NonNull {}");
    fixture.addClass("package org.checkerframework.checker.nullness.qual;import java.lang.annotation.*;" +
                     "@Target(ElementType.TYPE_USE)public @interface Nullable {}");
    fixture.addClass("package org.checkerframework.framework.qual;" +
                     "import java.lang.annotation.*;" +
                     "enum TypeUseLocation {ALL}" +
                     "public @interface DefaultQualifier {" +
                     "  Class<? extends Annotation> value();" +
                     "  TypeUseLocation[] locations() default {TypeUseLocation.ALL};" +
                     "}");
  }

  static void addJetBrainsNotNullByDefault(JavaCodeInsightTestFixture fixture) {
    fixture.addClass("""
                         package org.jetbrains.annotations;
                         
                         import java.lang.annotation.*;
                         
                         @Target({ElementType.TYPE, ElementType.PACKAGE})\s
                         public @interface NotNullByDefault {}""");
  }

  static void addJSpecifyNullMarked(JavaCodeInsightTestFixture fixture) {
    @Language("JAVA") String nullMarked =
      """
        package org.jspecify.annotations;
        import java.lang.annotation.*;
        @Target({ElementType.TYPE, ElementType.MODULE})
        public @interface NullMarked {}""";
    fixture.addClass(nullMarked);
  }

  public static void setupTypeUseAnnotations(String pkg, JavaCodeInsightTestFixture fixture) {
   setupCustomAnnotations(pkg, "{ElementType.TYPE_USE}", fixture);
 }

  private static void setupCustomAnnotations(String pkg, String target, JavaCodeInsightTestFixture fixture) {
   fixture.addClass("package " + pkg + ";\n\nimport java.lang.annotation.*;\n\n@Target(" + target + ") public @interface Nullable { }");
   fixture.addClass("package " + pkg + ";\n\nimport java.lang.annotation.*;\n\n@Target(" + target + ") public @interface NotNull { }");
   setCustomAnnotations(fixture.getProject(), fixture.getTestRootDisposable(), pkg + ".NotNull", pkg + ".Nullable");
 }

  static void setCustomAnnotations(Project project, Disposable parentDisposable, String notNull, String nullable) {
   NullableNotNullManager nnnManager = NullableNotNullManager.getInstance(project);
   nnnManager.setNotNulls(notNull);
   nnnManager.setNullables(nullable);
   Disposer.register(parentDisposable, () -> {
     nnnManager.setNotNulls();
     nnnManager.setNullables();
   });
 }

  static void setupAmbiguousAnnotations(String pkg, JavaCodeInsightTestFixture fixture) {
    setupCustomAnnotations(pkg, "{ElementType.METHOD, ElementType.TYPE_USE}", fixture);
  }
}