// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection

import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.util.PathUtil
import org.jetbrains.annotations.ApiStatus

abstract class UnstableApiUsageInspectionTestBase : JavaCodeInsightFixtureTestCase() {
  abstract override fun getBasePath(): String

  override fun tuneFixture(moduleBuilder: JavaModuleFixtureBuilder<*>) {
    moduleBuilder.addLibrary("util", PathUtil.getJarPathForClass(ApiStatus.Experimental::class.java))
  }

  open fun performAdditionalSetUp() {}

  override fun setUp() {
    super.setUp()
    performAdditionalSetUp()

    myFixture.enableInspections(UnstableApiUsageInspection::class.java)

    myFixture.addFileToProject(
      "pkg/ExperimentalAnnotation.java", """
      package pkg;
      import org.jetbrains.annotations.ApiStatus;
      @ApiStatus.Experimental public @interface ExperimentalAnnotation {
        String nonExperimentalAttributeInExperimentalAnnotation() default "";
        @ApiStatus.Experimental String experimentalAttributeInExperimentalAnnotation() default "";
      }"""
    )
    myFixture.addFileToProject(
      "pkg/ExperimentalEnum.java", """
      package pkg;
      import org.jetbrains.annotations.ApiStatus;
      @ApiStatus.Experimental public enum ExperimentalEnum {
        NON_EXPERIMENTAL_VALUE_IN_EXPERIMENTAL_ENUM,
        @ApiStatus.Experimental EXPERIMENTAL_VALUE_IN_EXPERIMENTAL_ENUM
      }"""
    )
    myFixture.addFileToProject(
      "pkg/ExperimentalClass.java", """
      package pkg;
      import org.jetbrains.annotations.ApiStatus;
      @ApiStatus.Experimental public class ExperimentalClass {
        public static final String NON_EXPERIMENTAL_CONSTANT_IN_EXPERIMENTAL_CLASS = "";
        public String nonExperimentalFieldInExperimentalClass = "";
        public ExperimentalClass() {}
        public static void staticNonExperimentalMethodInExperimentalClass() {}
        public void nonExperimentalMethodInExperimentalClass() {}

        @ApiStatus.Experimental public static final String EXPERIMENTAL_CONSTANT_IN_EXPERIMENTAL_CLASS = "";
        @ApiStatus.Experimental public String experimentalFieldInExperimentalClass = "";
        @ApiStatus.Experimental public ExperimentalClass(String s) {}
        @ApiStatus.Experimental public static void staticExperimentalMethodInExperimentalClass() {}
        @ApiStatus.Experimental public void experimentalMethodInExperimentalClass() {}
      }"""
    )


    myFixture.addFileToProject(
      "pkg/NonExperimentalAnnotation.java", """
      package pkg;
      import org.jetbrains.annotations.ApiStatus;
      public @interface NonExperimentalAnnotation {
        String nonExperimentalAttributeInNonExperimentalAnnotation() default "";
        @ApiStatus.Experimental String experimentalAttributeInNonExperimentalAnnotation() default "";
      }"""
    )
    myFixture.addFileToProject(
      "pkg/NonExperimentalEnum.java", """
      package pkg;
      import org.jetbrains.annotations.ApiStatus;
      public enum NonExperimentalEnum {
        NON_EXPERIMENTAL_VALUE_IN_NON_EXPERIMENTAL_ENUM,
        @ApiStatus.Experimental EXPERIMENTAL_VALUE_IN_NON_EXPERIMENTAL_ENUM
      }"""
    )
    myFixture.addFileToProject(
      "pkg/NonExperimentalClass.java", """
      package pkg;
      import org.jetbrains.annotations.ApiStatus;
      public class NonExperimentalClass {
        public static final String NON_EXPERIMENTAL_CONSTANT_IN_NON_EXPERIMENTAL_CLASS = "";
        public String nonExperimentalFieldInNonExperimentalClass = "";
        public NonExperimentalClass() {}
        public static void staticNonExperimentalMethodInNonExperimentalClass() {}
        public void nonExperimentalMethodInNonExperimentalClass() {}

        @ApiStatus.Experimental public static final String EXPERIMENTAL_CONSTANT_IN_NON_EXPERIMENTAL_CLASS = "";
        @ApiStatus.Experimental public String experimentalFieldInNonExperimentalClass = "";
        @ApiStatus.Experimental public NonExperimentalClass(String s) {}
        @ApiStatus.Experimental public static void staticExperimentalMethodInNonExperimentalClass() {}
        @ApiStatus.Experimental public void experimentalMethodInNonExperimentalClass() {}
      }"""
    )


    myFixture.addFileToProject("unstablePkg/package-info.java",
                              "@org.jetbrains.annotations.ApiStatus.Experimental\n" +
                              "package unstablePkg;")
    myFixture.addFileToProject("unstablePkg/ClassInUnstablePkg.java",
                              "package unstablePkg;\n" +
                              "public class ClassInUnstablePkg {}")
  }
}