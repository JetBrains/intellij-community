package com.intellij.codeInspection.tests

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase

abstract class AnnotatedElementUsageInspectionTestBase : JavaCodeInsightFixtureTestCase() {
  abstract override fun getBasePath(): String
  abstract fun getInspection(): InspectionProfileEntry
  abstract fun getAnnotationFqn(): String

  open fun performAdditionalSetUp() {}

  override fun setUp() {
    super.setUp()
    performAdditionalSetUp()
    myFixture.enableInspections(getInspection())

    addAnnotatedElementsToProject("@" + getAnnotationFqn())
  }

  private fun addAnnotatedElementsToProject(annotation: String) {
    myFixture.addFileToProject(
      "pkg/AnnotatedAnnotation.java", """
      package pkg;
      ${annotation} public @interface AnnotatedAnnotation {
        String nonAnnotatedAttributeInAnnotatedAnnotation() default "";
        ${annotation} String annotatedAttributeInAnnotatedAnnotation() default "";
      }"""
    )
    myFixture.addFileToProject(
      "pkg/AnnotatedEnum.java", """
      package pkg;
      ${annotation} public enum AnnotatedEnum {
        NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM,
        ${annotation} ANNOTATED_VALUE_IN_ANNOTATED_ENUM
      }"""
    )
    myFixture.addFileToProject(
      "pkg/AnnotatedClass.java", """
      package pkg;
      ${annotation} public class AnnotatedClass {
        public static final String NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS = "";
        public String nonAnnotatedFieldInAnnotatedClass = "";
        public AnnotatedClass() {}
        public static void staticNonAnnotatedMethodInAnnotatedClass() {}
        public void nonAnnotatedMethodInAnnotatedClass() {}

        ${annotation} public static final String ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS = "";
        ${annotation} public String annotatedFieldInAnnotatedClass = "";
        ${annotation} public AnnotatedClass(String s) {}
        ${annotation} public static void staticAnnotatedMethodInAnnotatedClass() {}
        ${annotation} public void annotatedMethodInAnnotatedClass() {}
      }"""
    )


    myFixture.addFileToProject(
      "pkg/NonAnnotatedAnnotation.java", """
      package pkg;
      public @interface NonAnnotatedAnnotation {
        String nonAnnotatedAttributeInNonAnnotatedAnnotation() default "";
        ${annotation} String annotatedAttributeInNonAnnotatedAnnotation() default "";
      }"""
    )
    myFixture.addFileToProject(
      "pkg/NonAnnotatedEnum.java", """
      package pkg;
      public enum NonAnnotatedEnum {
        NON_ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM,
        ${annotation} ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM
      }"""
    )
    myFixture.addFileToProject(
      "pkg/NonAnnotatedClass.java", """
      package pkg;
      public class NonAnnotatedClass {
        public static final String NON_ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS = "";
        public String nonAnnotatedFieldInNonAnnotatedClass = "";
        public NonAnnotatedClass() {}
        public static void staticNonAnnotatedMethodInNonAnnotatedClass() {}
        public void nonAnnotatedMethodInNonAnnotatedClass() {}

        ${annotation} public static final String ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS = "";
        ${annotation} public String annotatedFieldInNonAnnotatedClass = "";
        ${annotation} public NonAnnotatedClass(String s) {}
        ${annotation} public static void staticAnnotatedMethodInNonAnnotatedClass() {}
        ${annotation} public void annotatedMethodInNonAnnotatedClass() {}
      }"""
    )


    myFixture.addFileToProject("annotatedPkg/package-info.java",
                               "${annotation}\n" +
                               "package annotatedPkg;")
    myFixture.addFileToProject("annotatedPkg/ClassInAnnotatedPkg.java",
                               "package annotatedPkg;\n" +
                               "public class ClassInAnnotatedPkg {}")
  }
}