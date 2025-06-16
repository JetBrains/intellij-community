package com.intellij.jvm.analysis.internal.testFramework

import com.intellij.codeInspection.UnstableApiUsageInspection
import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl

abstract class UnstableApiUsageInspectionTestBase : JvmInspectionTestBase() {
  override val inspection: UnstableApiUsageInspection by lazy { UnstableApiUsageInspection() } // lazy because inspection needs service in initialization

  override fun getProjectDescriptor(): LightProjectDescriptor = object : ProjectDescriptor(LanguageLevel.HIGHEST, true) {}

  override fun setUp() {
    super.setUp()
    // otherwise assertion in PsiFileImpl ("Access to tree elements not allowed") will not pass
    (myFixture as CodeInsightTestFixtureImpl).setVirtualFileFilter(VirtualFileFilter.NONE)
    setupExperimentalApi()
    setupScheduledForRemovalApi()
  }

  private fun setupScheduledForRemovalApi() {
    myFixture.addFileToProject("scheduledForRemoval/annotatedPkg/ClassInAnnotatedPkg.java", """
      package scheduledForRemoval.annotatedPkg;
      public class ClassInAnnotatedPkg { }
    """.trimIndent())
    myFixture.addFileToProject("scheduledForRemoval/annotatedPkg/package-info.java", """
      @org.jetbrains.annotations.ApiStatus.ScheduledForRemoval(inVersion = "123.456")
      package scheduledForRemoval.annotatedPkg;
    """.trimIndent())

    myFixture.addFileToProject("scheduledForRemoval/pkg/AnnotatedAnnotation.java", """
      package scheduledForRemoval.pkg;

      import org.jetbrains.annotations.ApiStatus;

      @ApiStatus.ScheduledForRemoval(inVersion = "123.456")
      public @interface AnnotatedAnnotation {
        String nonAnnotatedAttributeInAnnotatedAnnotation() default "";

        @ApiStatus.ScheduledForRemoval(inVersion = "123.456") String annotatedAttributeInAnnotatedAnnotation() default "";
      }
    """.trimIndent())
    myFixture.addFileToProject("scheduledForRemoval/pkg/AnnotatedClass.java", """
      package scheduledForRemoval.pkg;
      
      import org.jetbrains.annotations.ApiStatus;
      
      @ApiStatus.ScheduledForRemoval(inVersion = "123.456")
      public class AnnotatedClass {
        public static final String NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS = "";
        public String nonAnnotatedFieldInAnnotatedClass = "";
        public AnnotatedClass() {}
        public static void staticNonAnnotatedMethodInAnnotatedClass() {}
        public void nonAnnotatedMethodInAnnotatedClass() {}
        @ApiStatus.ScheduledForRemoval(inVersion = "123.456") public static final String ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS = "";
        @ApiStatus.ScheduledForRemoval(inVersion = "123.456") public String annotatedFieldInAnnotatedClass = "";
        @ApiStatus.ScheduledForRemoval(inVersion = "123.456") public AnnotatedClass(String s) {}
        @ApiStatus.ScheduledForRemoval(inVersion = "123.456") public static void staticAnnotatedMethodInAnnotatedClass() {}
        @ApiStatus.ScheduledForRemoval(inVersion = "123.456") public void annotatedMethodInAnnotatedClass() {}
      }
    """.trimIndent())
    myFixture.addFileToProject("scheduledForRemoval/pkg/AnnotatedEnum.java", """
      package scheduledForRemoval.pkg;
      
      import org.jetbrains.annotations.ApiStatus;
      
      @ApiStatus.ScheduledForRemoval(inVersion = "123.456")
      public enum AnnotatedEnum {
        NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM,
        @ApiStatus.ScheduledForRemoval(inVersion = "123.456") ANNOTATED_VALUE_IN_ANNOTATED_ENUM
      }
    """.trimIndent())
    myFixture.addFileToProject("scheduledForRemoval/pkg/ClassWithScheduledForRemovalTypeInSignature.java", """
      package scheduledForRemoval.pkg;
      
      public class ClassWithScheduledForRemovalTypeInSignature<T extends AnnotatedClass> { }
    """.trimIndent())
    myFixture.addFileToProject("scheduledForRemoval/pkg/NonAnnotatedAnnotation.java", """
      package scheduledForRemoval.pkg;
      
      import org.jetbrains.annotations.ApiStatus;
      
      public @interface NonAnnotatedAnnotation {
        String nonAnnotatedAttributeInNonAnnotatedAnnotation() default "";
        @ApiStatus.ScheduledForRemoval(inVersion = "123.456") String annotatedAttributeInNonAnnotatedAnnotation() default "";
      }
    """.trimIndent())
    myFixture.addFileToProject("scheduledForRemoval/pkg/NonAnnotatedClass.java", """
      package scheduledForRemoval.pkg;
      
      import org.jetbrains.annotations.ApiStatus;
      
      public class NonAnnotatedClass {
        public static final String NON_ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS = "";
        public String nonAnnotatedFieldInNonAnnotatedClass = "";
        public NonAnnotatedClass() { }
        public static void staticNonAnnotatedMethodInNonAnnotatedClass() { }
        public void nonAnnotatedMethodInNonAnnotatedClass() { }
        @ApiStatus.ScheduledForRemoval(inVersion = "123.456") public static final String ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS = "";
        @ApiStatus.ScheduledForRemoval(inVersion = "123.456") public String annotatedFieldInNonAnnotatedClass = "";
        @ApiStatus.ScheduledForRemoval(inVersion = "123.456") public NonAnnotatedClass(String s) { }
        @ApiStatus.ScheduledForRemoval(inVersion = "123.456") public static void staticAnnotatedMethodInNonAnnotatedClass() { }
        @ApiStatus.ScheduledForRemoval(inVersion = "123.456") public void annotatedMethodInNonAnnotatedClass() { }
      }
    """.trimIndent())
    myFixture.addFileToProject("scheduledForRemoval/pkg/NonAnnotatedEnum.java", """
      package scheduledForRemoval.pkg;
      
      import org.jetbrains.annotations.ApiStatus;
      
      public enum NonAnnotatedEnum {
        NON_ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM,
        @ApiStatus.ScheduledForRemoval(inVersion = "123.456") ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM
      }
    """.trimIndent())
    myFixture.addFileToProject("scheduledForRemoval/pkg/OwnerOfMembersWithScheduledForRemovalTypesInSignature.java", """
      package scheduledForRemoval.pkg;
      
      import scheduledForRemoval.annotatedPkg.ClassInAnnotatedPkg;
      
      public class OwnerOfMembersWithScheduledForRemovalTypesInSignature {
        public AnnotatedClass field;
        public ClassInAnnotatedPkg fieldPkg;
        public void parameterType(AnnotatedClass param) { }
        public void parameterTypePkg(ClassInAnnotatedPkg param) { }
        public AnnotatedClass returnType() { return null; }
        public AnnotatedClass returnTypePkg() { return null; }
      }
    """.trimIndent())
  }

  private fun setupExperimentalApi() {
    myFixture.addFileToProject("experimental/annotatedPkg/ClassInAnnotatedPkg.java", """
      package experimental.annotatedPkg;
      public class ClassInAnnotatedPkg { }
    """.trimIndent())
    myFixture.addFileToProject("experimental/annotatedPkg/package-info.java", """
      @org.jetbrains.annotations.ApiStatus.Experimental
      package experimental.annotatedPkg;
    """.trimIndent())

    myFixture.addFileToProject("experimental/pkg/AnnotatedAnnotation.java", """
      package experimental.pkg;

      import org.jetbrains.annotations.ApiStatus;

      @ApiStatus.Experimental
      public @interface AnnotatedAnnotation {
        String nonAnnotatedAttributeInAnnotatedAnnotation() default "";

        @ApiStatus.Experimental String annotatedAttributeInAnnotatedAnnotation() default "";
      }
    """.trimIndent())
    myFixture.addFileToProject("experimental/pkg/AnnotatedClass.java", """
      package experimental.pkg;

      import org.jetbrains.annotations.ApiStatus;

      @ApiStatus.Experimental
      public class AnnotatedClass {
        public static final String NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS = "";
        public String nonAnnotatedFieldInAnnotatedClass = "";
        public AnnotatedClass() {}
        public static void staticNonAnnotatedMethodInAnnotatedClass() {}
        public void nonAnnotatedMethodInAnnotatedClass() {}
        @ApiStatus.Experimental public static final String ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS = "";
        @ApiStatus.Experimental public String annotatedFieldInAnnotatedClass = "";
        @ApiStatus.Experimental public AnnotatedClass(String s) {}
        @ApiStatus.Experimental public static void staticAnnotatedMethodInAnnotatedClass() {}
        @ApiStatus.Experimental public void annotatedMethodInAnnotatedClass() {}
      }
    """.trimIndent())
    myFixture.addFileToProject("experimental/pkg/AnnotatedEnum.java", """
      package experimental.pkg;

      import org.jetbrains.annotations.ApiStatus;

      @ApiStatus.Experimental
      public enum AnnotatedEnum {
        NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM,
        @ApiStatus.Experimental ANNOTATED_VALUE_IN_ANNOTATED_ENUM
      }
    """.trimIndent())
    myFixture.addFileToProject("experimental/pkg/ClassWithExperimentalTypeInSignature.java", """
      package experimental.pkg;

      public class ClassWithExperimentalTypeInSignature<T extends AnnotatedClass> { }
    """.trimIndent())
    myFixture.addFileToProject("experimental/pkg/NonAnnotatedAnnotation.java", """
      package experimental.pkg;

      import org.jetbrains.annotations.ApiStatus;

      public @interface NonAnnotatedAnnotation {
        String nonAnnotatedAttributeInNonAnnotatedAnnotation() default "";
        @ApiStatus.Experimental String annotatedAttributeInNonAnnotatedAnnotation() default "";
      }
    """.trimIndent())
    myFixture.addFileToProject("experimental/pkg/NonAnnotatedClass.java", """
      package experimental.pkg;

      import org.jetbrains.annotations.ApiStatus;

      public class NonAnnotatedClass {
        public static final String NON_ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS = "";
        public String nonAnnotatedFieldInNonAnnotatedClass = "";
        public NonAnnotatedClass() {}
        public static void staticNonAnnotatedMethodInNonAnnotatedClass() {}
        public void nonAnnotatedMethodInNonAnnotatedClass() {}
        @ApiStatus.Experimental public static final String ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS = "";
        @ApiStatus.Experimental public String annotatedFieldInNonAnnotatedClass = "";
        @ApiStatus.Experimental public NonAnnotatedClass(String s) {}
        @ApiStatus.Experimental public static void staticAnnotatedMethodInNonAnnotatedClass() {}
        @ApiStatus.Experimental public void annotatedMethodInNonAnnotatedClass() {}
      }
    """.trimIndent())
    myFixture.addFileToProject("experimental/NonAnnotatedEnum.java", """
      package experimental.pkg;

      import org.jetbrains.annotations.ApiStatus;

      public enum NonAnnotatedEnum {
        NON_ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM,
        @ApiStatus.Experimental ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM
      }
    """.trimIndent())
    myFixture.addFileToProject("experimental/OwnerOfMembersWithExperimentalTypesInSignature.java", """
      package experimental.pkg;

      import experimental.annotatedPkg.ClassInAnnotatedPkg;

      public class OwnerOfMembersWithExperimentalTypesInSignature {
        public AnnotatedClass field;
        public ClassInAnnotatedPkg fieldPkg;
        public void parameterType(AnnotatedClass param) { }
        public void parameterTypePkg(ClassInAnnotatedPkg param) { }
        public AnnotatedClass returnType() { return null; }
        public AnnotatedClass returnTypePkg() { return null; }
      }
    """.trimIndent())
  }
}
