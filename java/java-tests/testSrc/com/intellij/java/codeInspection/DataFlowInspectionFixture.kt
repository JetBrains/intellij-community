// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection

import com.intellij.codeInsight.NullableNotNullManager
import com.intellij.codeInspection.dataFlow.ConstantValueInspection
import com.intellij.codeInspection.dataFlow.DataFlowInspection
import com.intellij.java.codeInsight.JSpecifyTestUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.annotations.TestOnly
import java.util.function.BiConsumer

/**
 * The fixture of the data flow inspection tests. Create it with [dataFlowInspectionFixture].
 *
 * The members enable [DataFlowInspection] and [ConstantValueInspection], and check the highlighting of the test file.
 * The companion holds the helpers that add the nullability annotations to the project.
 */
@TestOnly
class DataFlowInspectionFixture internal constructor(
  /**
   * The code insight fixture of the test.
   */
  val fixture: LightJavaCodeInsightTestFixture,
  private val testFileDirectory: String,
) {
  /**
   * Returns the name of the test method without the `test` prefix, as [LightJavaCodeInsightTestFixture.getTestName] does.
   */
  fun getTestName(lowercaseFirstLetter: Boolean): String = fixture.getTestName(lowercaseFirstLetter)

  /**
   * Checks the highlighting of the test file with the suggestion of nullable annotations,
   * and without the report of constant reference values.
   */
  fun doTest() {
    doTestWith { df, cv ->
      df.SUGGEST_NULLABLE_ANNOTATIONS = true
      cv.REPORT_CONSTANT_REFERENCE_VALUES = false
    }
  }

  /**
   * Checks the highlighting of the test file with the inspections that [inspectionMutator] sets up.
   */
  fun doTestWith(inspectionMutator: BiConsumer<DataFlowInspection, ConstantValueInspection>) {
    val inspection = DataFlowInspection()
    val cvInspection = ConstantValueInspection()
    inspectionMutator.accept(inspection, cvInspection)
    fixture.enableInspections(inspection, cvInspection)
    fixture.testHighlighting(true, false, true, getTestFileName())
  }

  /**
   * Returns the test file: `<test file directory><test name>.java` of the test data.
   */
  fun getTestFileName(): String = testFileDirectory + getTestName(false) + ".java"

  companion object {
    @JvmStatic
    fun addCheckerAnnotations(fixture: JavaCodeInsightTestFixture) {
      fixture.addClass("package org.checkerframework.checker.nullness.qual;import java.lang.annotation.*;" +
                       "@Target(ElementType.TYPE_USE)public @interface NonNull {}")
      fixture.addClass("package org.checkerframework.checker.nullness.qual;import java.lang.annotation.*;" +
                       "@Target(ElementType.TYPE_USE)public @interface Nullable {}")
      fixture.addClass("package org.checkerframework.framework.qual;" +
                       "import java.lang.annotation.*;" +
                       "enum TypeUseLocation {ALL}" +
                       "public @interface DefaultQualifier {" +
                       "  Class<? extends Annotation> value();" +
                       "  TypeUseLocation[] locations() default {TypeUseLocation.ALL};" +
                       "}")
    }

    @JvmStatic
    fun addJSpecifyNullMarked(fixture: JavaCodeInsightTestFixture) {
      JSpecifyTestUtil.addJSpecifyNullMarked(fixture)
    }

    @JvmStatic
    fun addJSpecifyNonNull(fixture: JavaCodeInsightTestFixture) {
      JSpecifyTestUtil.addJSpecifyNonNull(fixture)
    }

    /**
     * Adds the `Nullable` and `NotNull` type-use annotations of [pkg], and makes them the project nullability annotations.
     */
    @JvmStatic
    fun setupTypeUseAnnotations(pkg: String, fixture: JavaCodeInsightTestFixture) {
      setupCustomAnnotations(pkg, "{ElementType.TYPE_USE}", fixture)
    }

    /**
     * Adds the `Nullable` and `NotNull` annotations of [pkg] for methods and type uses,
     * and makes them the project nullability annotations.
     */
    @JvmStatic
    fun setupAmbiguousAnnotations(pkg: String, fixture: JavaCodeInsightTestFixture) {
      setupCustomAnnotations(pkg, "{ElementType.METHOD, ElementType.TYPE_USE}", fixture)
    }

    /**
     * Makes [notNull] and [nullable] the nullability annotations of [project] until [parentDisposable] is disposed.
     */
    @JvmStatic
    fun setCustomAnnotations(project: Project, parentDisposable: Disposable, notNull: String, nullable: String) {
      val nnnManager = NullableNotNullManager.getInstance(project)
      nnnManager.setNotNulls(notNull)
      nnnManager.setNullables(nullable)
      Disposer.register(parentDisposable) {
        nnnManager.setNotNulls()
        nnnManager.setNullables()
      }
    }

    private fun setupCustomAnnotations(pkg: String, target: String, fixture: JavaCodeInsightTestFixture) {
      fixture.addClass("package $pkg;\n\nimport java.lang.annotation.*;\n\n@Target($target) public @interface Nullable { }")
      fixture.addClass("package $pkg;\n\nimport java.lang.annotation.*;\n\n@Target($target) public @interface NotNull { }")
      setCustomAnnotations(fixture.project, fixture.testRootDisposable, "$pkg.NotNull", "$pkg.Nullable")
    }
  }
}

/**
 * Creates a [DataFlowInspectionFixture] for one test. Declare it as an instance field.
 *
 * [testDataPath] is the absolute test data path. When it is `null`, the fixture keeps the default path of [lightJavaCodeInsightFixture].
 * [testFileDirectory] is the directory of the test file in the test data, with a trailing `/`, or an empty string.
 */
@TestOnly
@JvmOverloads
fun dataFlowInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
  testDataPath: String? = null,
  testFileDirectory: String = "",
): TestFixture<DataFlowInspectionFixture> = testFixture("dataFlowInspectionFixture") {
  val fixture = lightJavaCodeInsightFixture(projectFixture).init()
  if (testDataPath != null) {
    fixture.testDataPath = testDataPath
  }
  initialized(DataFlowInspectionFixture(fixture, testFileDirectory)) {}
}
