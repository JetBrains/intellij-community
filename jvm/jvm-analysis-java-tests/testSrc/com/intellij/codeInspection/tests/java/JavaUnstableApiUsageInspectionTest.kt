package com.intellij.codeInspection.tests.java

import com.intellij.jvm.analysis.internal.testFramework.UnstableApiUsageInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage

class JavaUnstableApiUsageInspectionTest : UnstableApiUsageInspectionTestBase() {
  fun `test java experimental api usages`() {
    inspection.myIgnoreInsideImports = false
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import experimental.pkg.<warning descr="'experimental.pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning>;
      import experimental.pkg.<warning descr="'experimental.pkg.ClassWithExperimentalTypeInSignature' is unstable because its signature references unstable class 'experimental.pkg.AnnotatedClass' marked with @ApiStatus.Experimental">ClassWithExperimentalTypeInSignature</warning>;
      import experimental.pkg.OwnerOfMembersWithExperimentalTypesInSignature;
      import static experimental.pkg.<warning descr="'experimental.pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning>.<warning descr="'NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is declared in unstable class 'experimental.pkg.AnnotatedClass' marked with @ApiStatus.Experimental">NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</warning>;
      import static experimental.pkg.<warning descr="'experimental.pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning>.<warning descr="'staticNonAnnotatedMethodInAnnotatedClass()' is declared in unstable class 'experimental.pkg.AnnotatedClass' marked with @ApiStatus.Experimental">staticNonAnnotatedMethodInAnnotatedClass</warning>;
      import static experimental.pkg.<warning descr="'experimental.pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning>.<warning descr="'ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is marked unstable with @ApiStatus.Experimental">ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</warning>;
      import static experimental.pkg.<warning descr="'experimental.pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning>.<warning descr="'staticAnnotatedMethodInAnnotatedClass()' is marked unstable with @ApiStatus.Experimental">staticAnnotatedMethodInAnnotatedClass</warning>;

      import experimental.pkg.NonAnnotatedClass;
      import static experimental.pkg.NonAnnotatedClass.NON_ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS;
      import static experimental.pkg.NonAnnotatedClass.staticNonAnnotatedMethodInNonAnnotatedClass;
      import static experimental.pkg.NonAnnotatedClass.<warning descr="'ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS' is marked unstable with @ApiStatus.Experimental">ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS</warning>;
      import static experimental.pkg.NonAnnotatedClass.<warning descr="'staticAnnotatedMethodInNonAnnotatedClass()' is marked unstable with @ApiStatus.Experimental">staticAnnotatedMethodInNonAnnotatedClass</warning>;

      import experimental.pkg.<warning descr="'experimental.pkg.AnnotatedEnum' is marked unstable with @ApiStatus.Experimental">AnnotatedEnum</warning>;
      import experimental.pkg.NonAnnotatedEnum;
      import static experimental.pkg.<warning descr="'experimental.pkg.AnnotatedEnum' is marked unstable with @ApiStatus.Experimental">AnnotatedEnum</warning>.<warning descr="'NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is declared in unstable enum 'experimental.pkg.AnnotatedEnum' marked with @ApiStatus.Experimental">NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM</warning>;
      import static experimental.pkg.<warning descr="'experimental.pkg.AnnotatedEnum' is marked unstable with @ApiStatus.Experimental">AnnotatedEnum</warning>.<warning descr="'ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is marked unstable with @ApiStatus.Experimental">ANNOTATED_VALUE_IN_ANNOTATED_ENUM</warning>;
      import static experimental.pkg.NonAnnotatedEnum.NON_ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM;
      import static experimental.pkg.NonAnnotatedEnum.<warning descr="'ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM' is marked unstable with @ApiStatus.Experimental">ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM</warning>;

      import experimental.pkg.<warning descr="'experimental.pkg.AnnotatedAnnotation' is marked unstable with @ApiStatus.Experimental">AnnotatedAnnotation</warning>;
      import experimental.pkg.NonAnnotatedAnnotation;

      import experimental.<warning descr="'experimental.annotatedPkg' is marked unstable with @ApiStatus.Experimental">annotatedPkg</warning>.<warning descr="'experimental.annotatedPkg.ClassInAnnotatedPkg' is declared in unstable package 'experimental.annotatedPkg' marked with @ApiStatus.Experimental">ClassInAnnotatedPkg</warning>;

      class UnstableElementsTest {
        public void test() {
          String s = <warning descr="'experimental.pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning>.<warning descr="'NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is declared in unstable class 'experimental.pkg.AnnotatedClass' marked with @ApiStatus.Experimental">NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</warning>;
          <warning descr="'experimental.pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning>.<warning descr="'staticNonAnnotatedMethodInAnnotatedClass()' is declared in unstable class 'experimental.pkg.AnnotatedClass' marked with @ApiStatus.Experimental">staticNonAnnotatedMethodInAnnotatedClass</warning>();
          <warning descr="'experimental.pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning> annotatedClassInstanceViaNonAnnotatedConstructor = new <warning descr="'AnnotatedClass()' is declared in unstable class 'experimental.pkg.AnnotatedClass' marked with @ApiStatus.Experimental"><warning descr="'experimental.pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning></warning>();
          s = annotatedClassInstanceViaNonAnnotatedConstructor.<warning descr="'nonAnnotatedFieldInAnnotatedClass' is declared in unstable class 'experimental.pkg.AnnotatedClass' marked with @ApiStatus.Experimental">nonAnnotatedFieldInAnnotatedClass</warning>;
          annotatedClassInstanceViaNonAnnotatedConstructor.<warning descr="'nonAnnotatedMethodInAnnotatedClass()' is declared in unstable class 'experimental.pkg.AnnotatedClass' marked with @ApiStatus.Experimental">nonAnnotatedMethodInAnnotatedClass</warning>();
          s = <warning descr="'NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is declared in unstable class 'experimental.pkg.AnnotatedClass' marked with @ApiStatus.Experimental">NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</warning>;
          <warning descr="'staticNonAnnotatedMethodInAnnotatedClass()' is declared in unstable class 'experimental.pkg.AnnotatedClass' marked with @ApiStatus.Experimental">staticNonAnnotatedMethodInAnnotatedClass</warning>();

          s = <warning descr="'experimental.pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning>.<warning descr="'ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is marked unstable with @ApiStatus.Experimental">ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</warning>;
          <warning descr="'experimental.pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning>.<warning descr="'staticAnnotatedMethodInAnnotatedClass()' is marked unstable with @ApiStatus.Experimental">staticAnnotatedMethodInAnnotatedClass</warning>();
          <warning descr="'experimental.pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning> annotatedClassInstanceViaAnnotatedConstructor = new <warning descr="'AnnotatedClass(java.lang.String)' is marked unstable with @ApiStatus.Experimental"><warning descr="'experimental.pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning></warning>("");
          s = annotatedClassInstanceViaAnnotatedConstructor.<warning descr="'annotatedFieldInAnnotatedClass' is marked unstable with @ApiStatus.Experimental">annotatedFieldInAnnotatedClass</warning>;
          annotatedClassInstanceViaAnnotatedConstructor.<warning descr="'annotatedMethodInAnnotatedClass()' is marked unstable with @ApiStatus.Experimental">annotatedMethodInAnnotatedClass</warning>();
          s = <warning descr="'ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is marked unstable with @ApiStatus.Experimental">ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</warning>;
          <warning descr="'staticAnnotatedMethodInAnnotatedClass()' is marked unstable with @ApiStatus.Experimental">staticAnnotatedMethodInAnnotatedClass</warning>();

          // ---------------------------------

          s = NonAnnotatedClass.NON_ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS;
          NonAnnotatedClass.staticNonAnnotatedMethodInNonAnnotatedClass();
          NonAnnotatedClass nonAnnotatedClassInstanceViaNonAnnotatedConstructor = new NonAnnotatedClass();
          s = nonAnnotatedClassInstanceViaNonAnnotatedConstructor.nonAnnotatedFieldInNonAnnotatedClass;
          nonAnnotatedClassInstanceViaNonAnnotatedConstructor.nonAnnotatedMethodInNonAnnotatedClass();
          s = NON_ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS;
          staticNonAnnotatedMethodInNonAnnotatedClass();

          s = NonAnnotatedClass.<warning descr="'ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS' is marked unstable with @ApiStatus.Experimental">ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS</warning>;
          NonAnnotatedClass.<warning descr="'staticAnnotatedMethodInNonAnnotatedClass()' is marked unstable with @ApiStatus.Experimental">staticAnnotatedMethodInNonAnnotatedClass</warning>();
          NonAnnotatedClass nonAnnotatedClassInstanceViaAnnotatedConstructor = new <warning descr="'NonAnnotatedClass(java.lang.String)' is marked unstable with @ApiStatus.Experimental">NonAnnotatedClass</warning>("");
          s = nonAnnotatedClassInstanceViaAnnotatedConstructor.<warning descr="'annotatedFieldInNonAnnotatedClass' is marked unstable with @ApiStatus.Experimental">annotatedFieldInNonAnnotatedClass</warning>;
          nonAnnotatedClassInstanceViaAnnotatedConstructor.<warning descr="'annotatedMethodInNonAnnotatedClass()' is marked unstable with @ApiStatus.Experimental">annotatedMethodInNonAnnotatedClass</warning>();
          s = <warning descr="'ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS' is marked unstable with @ApiStatus.Experimental">ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS</warning>;
          <warning descr="'staticAnnotatedMethodInNonAnnotatedClass()' is marked unstable with @ApiStatus.Experimental">staticAnnotatedMethodInNonAnnotatedClass</warning>();

          // ---------------------------------

          <warning descr="'experimental.pkg.AnnotatedEnum' is marked unstable with @ApiStatus.Experimental">AnnotatedEnum</warning> nonAnnotatedValueInAnnotatedEnum = <warning descr="'experimental.pkg.AnnotatedEnum' is marked unstable with @ApiStatus.Experimental">AnnotatedEnum</warning>.<warning descr="'NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is declared in unstable enum 'experimental.pkg.AnnotatedEnum' marked with @ApiStatus.Experimental">NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM</warning>;
          nonAnnotatedValueInAnnotatedEnum = <warning descr="'NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is declared in unstable enum 'experimental.pkg.AnnotatedEnum' marked with @ApiStatus.Experimental">NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM</warning>;
          <warning descr="'experimental.pkg.AnnotatedEnum' is marked unstable with @ApiStatus.Experimental">AnnotatedEnum</warning> annotatedValueInAnnotatedEnum = <warning descr="'experimental.pkg.AnnotatedEnum' is marked unstable with @ApiStatus.Experimental">AnnotatedEnum</warning>.<warning descr="'ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is marked unstable with @ApiStatus.Experimental">ANNOTATED_VALUE_IN_ANNOTATED_ENUM</warning>;
          annotatedValueInAnnotatedEnum = <warning descr="'ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is marked unstable with @ApiStatus.Experimental">ANNOTATED_VALUE_IN_ANNOTATED_ENUM</warning>;

          NonAnnotatedEnum nonAnnotatedValueInNonAnnotatedEnum = NonAnnotatedEnum.NON_ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM;
          nonAnnotatedValueInNonAnnotatedEnum = NON_ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM;
          NonAnnotatedEnum annotatedValueInNonAnnotatedEnum = NonAnnotatedEnum.<warning descr="'ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM' is marked unstable with @ApiStatus.Experimental">ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM</warning>;
          annotatedValueInNonAnnotatedEnum = <warning descr="'ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM' is marked unstable with @ApiStatus.Experimental">ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM</warning>;
          
          // ---------------------------------

          @<warning descr="'experimental.pkg.AnnotatedAnnotation' is marked unstable with @ApiStatus.Experimental">AnnotatedAnnotation</warning> class C1 {}
          @<warning descr="'experimental.pkg.AnnotatedAnnotation' is marked unstable with @ApiStatus.Experimental">AnnotatedAnnotation</warning>(<warning descr="'nonAnnotatedAttributeInAnnotatedAnnotation' is declared in unstable annotation 'experimental.pkg.AnnotatedAnnotation' marked with @ApiStatus.Experimental">nonAnnotatedAttributeInAnnotatedAnnotation</warning> = "123") class C2 {}
          @<warning descr="'experimental.pkg.AnnotatedAnnotation' is marked unstable with @ApiStatus.Experimental">AnnotatedAnnotation</warning>(<warning descr="'annotatedAttributeInAnnotatedAnnotation' is marked unstable with @ApiStatus.Experimental">annotatedAttributeInAnnotatedAnnotation</warning> = "123") class C3 {}
          @NonAnnotatedAnnotation class C4 {}
          @NonAnnotatedAnnotation(nonAnnotatedAttributeInNonAnnotatedAnnotation = "123") class C5 {}
          @NonAnnotatedAnnotation(<warning descr="'annotatedAttributeInNonAnnotatedAnnotation' is marked unstable with @ApiStatus.Experimental">annotatedAttributeInNonAnnotatedAnnotation</warning> = "123") class C6 {}
        }
      }

      class DirectOverrideAnnotatedMethod extends NonAnnotatedClass {
        @Override
        public void <warning descr="Overridden method 'annotatedMethodInNonAnnotatedClass()' is marked unstable with @ApiStatus.Experimental">annotatedMethodInNonAnnotatedClass</warning>() {}
      }

      class IndirectOverrideAnnotatedMethod extends DirectOverrideAnnotatedMethod {
        @Override
        public void annotatedMethodInNonAnnotatedClass() {}
      }

      class <warning descr="'AnnotatedClass()' is declared in unstable class 'experimental.pkg.AnnotatedClass' marked with @ApiStatus.Experimental">DirectOverrideNonAnnotatedMethodInAnnotatedClass</warning> extends <warning descr="'experimental.pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning> {
        @Override
        public void <warning descr="Overridden method 'nonAnnotatedMethodInAnnotatedClass()' is declared in unstable class 'experimental.pkg.AnnotatedClass' marked with @ApiStatus.Experimental">nonAnnotatedMethodInAnnotatedClass</warning>() {}
      }

      class <warning descr="'AnnotatedClass()' is declared in unstable class 'experimental.pkg.AnnotatedClass' marked with @ApiStatus.Experimental">DirectOverrideAnnotatedMethodInAnnotatedClass</warning> extends <warning descr="'experimental.pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning> {
        @Override
        public void <warning descr="Overridden method 'annotatedMethodInAnnotatedClass()' is marked unstable with @ApiStatus.Experimental">annotatedMethodInAnnotatedClass</warning>() {}
      }

      //No warning should be produced.

      class WarningsOfExperimentalTypesInSignature {
        public void classUsage() {
          new <warning descr="'experimental.pkg.ClassWithExperimentalTypeInSignature' is unstable because its signature references unstable class 'experimental.pkg.AnnotatedClass' marked with @ApiStatus.Experimental">ClassWithExperimentalTypeInSignature<<warning descr="'experimental.pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning>></warning>();
        }

        public void membersUsages(OwnerOfMembersWithExperimentalTypesInSignature owner) {
          Object field = owner.<warning descr="'field' is unstable because its signature references unstable class 'experimental.pkg.AnnotatedClass' marked with @ApiStatus.Experimental">field</warning>;
          owner.<warning descr="'parameterType(experimental.pkg.AnnotatedClass)' is unstable because its signature references unstable class 'experimental.pkg.AnnotatedClass' marked with @ApiStatus.Experimental">parameterType</warning>(null);
          owner.<warning descr="'returnType()' is unstable because its signature references unstable class 'experimental.pkg.AnnotatedClass' marked with @ApiStatus.Experimental">returnType</warning>();

          Object fieldPkg = owner.<warning descr="'field' is unstable because its signature references unstable class 'experimental.pkg.AnnotatedClass' marked with @ApiStatus.Experimental">field</warning>;
          owner.<warning descr="'parameterTypePkg(experimental.annotatedPkg.ClassInAnnotatedPkg)' is unstable because its signature references unstable class 'experimental.annotatedPkg.ClassInAnnotatedPkg' marked with @ApiStatus.Experimental">parameterTypePkg</warning>(null);
          owner.<warning descr="'returnTypePkg()' is unstable because its signature references unstable class 'experimental.pkg.AnnotatedClass' marked with @ApiStatus.Experimental">returnTypePkg</warning>();
        }
      }
    """.trimIndent())
  }

  fun `test java do not report unstable api usages inside import statements`() {
    inspection.myIgnoreInsideImports = true
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import experimental.pkg.AnnotatedClass;
      import static experimental.pkg.AnnotatedClass.NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS;
      import static experimental.pkg.AnnotatedClass.staticNonAnnotatedMethodInAnnotatedClass;
      import static experimental.pkg.AnnotatedClass.ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS;
      import static experimental.pkg.AnnotatedClass.staticAnnotatedMethodInAnnotatedClass;

      import experimental.pkg.NonAnnotatedClass;
      import static experimental.pkg.NonAnnotatedClass.NON_ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS;
      import static experimental.pkg.NonAnnotatedClass.staticNonAnnotatedMethodInNonAnnotatedClass;
      import static experimental.pkg.NonAnnotatedClass.ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS;
      import static experimental.pkg.NonAnnotatedClass.staticAnnotatedMethodInNonAnnotatedClass;

      import experimental.pkg.AnnotatedEnum;
      import experimental.pkg.NonAnnotatedEnum;
      import static experimental.pkg.AnnotatedEnum.NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM;
      import static experimental.pkg.AnnotatedEnum.ANNOTATED_VALUE_IN_ANNOTATED_ENUM;
      import static experimental.pkg.NonAnnotatedEnum.NON_ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM;
      import static experimental.pkg.NonAnnotatedEnum.ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM;

      import experimental.pkg.AnnotatedAnnotation;
      import experimental.pkg.NonAnnotatedAnnotation;

      import experimental.annotatedPkg.ClassInAnnotatedPkg;
    """.trimIndent())
  }

  fun `test java no warnings on access to members of the same file`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      package test;

      import experimental.pkg.AnnotatedClass;

      @SuppressWarnings({"SameParameterValue", "unused", "UnusedReturnValue", "ResultOfMethodCallIgnored", "MethodMayBeStatic", "ObjectToString"})
      class NoWarningsMembersOfTheSameFile {
        private <warning descr="'experimental.pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning> field;
        private static <warning descr="'experimental.pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning> staticField;
        private <warning descr="'experimental.pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning> returnType() { return null; }
        private void paramType(<warning descr="'experimental.pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning> param) { }
        private static <warning descr="'experimental.pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning> staticReturnType() { return null; }
        private static void staticParamType(<warning descr="'experimental.pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning> a) { }

        void testNoWarningsProducedForMembersOfTheSameClass() {
          field.toString();
          staticField.toString();
          returnType();
          paramType(null);
          staticReturnType();
          staticParamType(null);
        }

        private class InnerClass {
          void testNoWarningsProducedForMembersEnclosingClass() {
            field.toString();
            staticField.toString();
            returnType();
            paramType(null);
            staticReturnType();
            staticParamType(null);
          }
        }
      }
    """.trimIndent())
  }

  fun `test highlighting scheduled for removal`() {
    inspection.myIgnoreInsideImports = false
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      // Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
      import scheduledForRemoval.pkg.<error descr="'scheduledForRemoval.pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</error>;
      import scheduledForRemoval.pkg.<error descr="'scheduledForRemoval.pkg.ClassWithScheduledForRemovalTypeInSignature' is scheduled for removal because its signature references class 'scheduledForRemoval.pkg.AnnotatedClass' scheduled for removal in version 123.456">ClassWithScheduledForRemovalTypeInSignature</error>;
      import scheduledForRemoval.pkg.OwnerOfMembersWithScheduledForRemovalTypesInSignature;
      import static scheduledForRemoval.pkg.<error descr="'scheduledForRemoval.pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</error>.<error descr="'NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is declared in class 'scheduledForRemoval.pkg.AnnotatedClass' scheduled for removal in version 123.456">NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</error>;
      import static scheduledForRemoval.pkg.<error descr="'scheduledForRemoval.pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</error>.<error descr="'staticNonAnnotatedMethodInAnnotatedClass()' is declared in class 'scheduledForRemoval.pkg.AnnotatedClass' scheduled for removal in version 123.456">staticNonAnnotatedMethodInAnnotatedClass</error>;
      import static scheduledForRemoval.pkg.<error descr="'scheduledForRemoval.pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</error>.<error descr="'ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is scheduled for removal in version 123.456">ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</error>;
      import static scheduledForRemoval.pkg.<error descr="'scheduledForRemoval.pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</error>.<error descr="'staticAnnotatedMethodInAnnotatedClass()' is scheduled for removal in version 123.456">staticAnnotatedMethodInAnnotatedClass</error>;

      import scheduledForRemoval.pkg.NonAnnotatedClass;
      import static scheduledForRemoval.pkg.NonAnnotatedClass.NON_ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS;
      import static scheduledForRemoval.pkg.NonAnnotatedClass.staticNonAnnotatedMethodInNonAnnotatedClass;
      import static scheduledForRemoval.pkg.NonAnnotatedClass.<error descr="'ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS' is scheduled for removal in version 123.456">ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS</error>;
      import static scheduledForRemoval.pkg.NonAnnotatedClass.<error descr="'staticAnnotatedMethodInNonAnnotatedClass()' is scheduled for removal in version 123.456">staticAnnotatedMethodInNonAnnotatedClass</error>;

      import scheduledForRemoval.pkg.<error descr="'scheduledForRemoval.pkg.AnnotatedEnum' is scheduled for removal in version 123.456">AnnotatedEnum</error>;
      import scheduledForRemoval.pkg.NonAnnotatedEnum;
      import static scheduledForRemoval.pkg.<error descr="'scheduledForRemoval.pkg.AnnotatedEnum' is scheduled for removal in version 123.456">AnnotatedEnum</error>.<error descr="'NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is declared in enum 'scheduledForRemoval.pkg.AnnotatedEnum' scheduled for removal in version 123.456">NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM</error>;
      import static scheduledForRemoval.pkg.<error descr="'scheduledForRemoval.pkg.AnnotatedEnum' is scheduled for removal in version 123.456">AnnotatedEnum</error>.<error descr="'ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is scheduled for removal in version 123.456">ANNOTATED_VALUE_IN_ANNOTATED_ENUM</error>;
      import static scheduledForRemoval.pkg.NonAnnotatedEnum.NON_ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM;
      import static scheduledForRemoval.pkg.NonAnnotatedEnum.<error descr="'ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM' is scheduled for removal in version 123.456">ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM</error>;

      import scheduledForRemoval.pkg.<error descr="'scheduledForRemoval.pkg.AnnotatedAnnotation' is scheduled for removal in version 123.456">AnnotatedAnnotation</error>;
      import scheduledForRemoval.pkg.NonAnnotatedAnnotation;

      import scheduledForRemoval.<error descr="'scheduledForRemoval.annotatedPkg' is scheduled for removal in version 123.456">annotatedPkg</error>.<error descr="'scheduledForRemoval.annotatedPkg.ClassInAnnotatedPkg' is declared in package 'scheduledForRemoval.annotatedPkg' scheduled for removal in version 123.456">ClassInAnnotatedPkg</error>;

      class ScheduledForRemovalElementsTest {
        public void test() {
          String s = <error descr="'scheduledForRemoval.pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</error>.<error descr="'NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is declared in class 'scheduledForRemoval.pkg.AnnotatedClass' scheduled for removal in version 123.456">NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</error>;
          <error descr="'scheduledForRemoval.pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</error>.<error descr="'staticNonAnnotatedMethodInAnnotatedClass()' is declared in class 'scheduledForRemoval.pkg.AnnotatedClass' scheduled for removal in version 123.456">staticNonAnnotatedMethodInAnnotatedClass</error>();
          <error descr="'scheduledForRemoval.pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</error> annotatedClassInstanceViaNonAnnotatedConstructor = new <error descr="'AnnotatedClass()' is declared in class 'scheduledForRemoval.pkg.AnnotatedClass' scheduled for removal in version 123.456"><error descr="'scheduledForRemoval.pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</error></error>();
          s = annotatedClassInstanceViaNonAnnotatedConstructor.<error descr="'nonAnnotatedFieldInAnnotatedClass' is declared in class 'scheduledForRemoval.pkg.AnnotatedClass' scheduled for removal in version 123.456">nonAnnotatedFieldInAnnotatedClass</error>;
          annotatedClassInstanceViaNonAnnotatedConstructor.<error descr="'nonAnnotatedMethodInAnnotatedClass()' is declared in class 'scheduledForRemoval.pkg.AnnotatedClass' scheduled for removal in version 123.456">nonAnnotatedMethodInAnnotatedClass</error>();
          s = <error descr="'NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is declared in class 'scheduledForRemoval.pkg.AnnotatedClass' scheduled for removal in version 123.456">NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</error>;
          <error descr="'staticNonAnnotatedMethodInAnnotatedClass()' is declared in class 'scheduledForRemoval.pkg.AnnotatedClass' scheduled for removal in version 123.456">staticNonAnnotatedMethodInAnnotatedClass</error>();

          s = <error descr="'scheduledForRemoval.pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</error>.<error descr="'ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is scheduled for removal in version 123.456">ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</error>;
          <error descr="'scheduledForRemoval.pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</error>.<error descr="'staticAnnotatedMethodInAnnotatedClass()' is scheduled for removal in version 123.456">staticAnnotatedMethodInAnnotatedClass</error>();
          <error descr="'scheduledForRemoval.pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</error> annotatedClassInstanceViaAnnotatedConstructor = new <error descr="'AnnotatedClass(java.lang.String)' is scheduled for removal in version 123.456"><error descr="'scheduledForRemoval.pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</error></error>("");
          s = annotatedClassInstanceViaAnnotatedConstructor.<error descr="'annotatedFieldInAnnotatedClass' is scheduled for removal in version 123.456">annotatedFieldInAnnotatedClass</error>;
          annotatedClassInstanceViaAnnotatedConstructor.<error descr="'annotatedMethodInAnnotatedClass()' is scheduled for removal in version 123.456">annotatedMethodInAnnotatedClass</error>();
          s = <error descr="'ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is scheduled for removal in version 123.456">ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</error>;
          <error descr="'staticAnnotatedMethodInAnnotatedClass()' is scheduled for removal in version 123.456">staticAnnotatedMethodInAnnotatedClass</error>();

          // ---------------------------------

          s = NonAnnotatedClass.NON_ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS;
          NonAnnotatedClass.staticNonAnnotatedMethodInNonAnnotatedClass();
          NonAnnotatedClass nonAnnotatedClassInstanceViaNonAnnotatedConstructor = new NonAnnotatedClass();
          s = nonAnnotatedClassInstanceViaNonAnnotatedConstructor.nonAnnotatedFieldInNonAnnotatedClass;
          nonAnnotatedClassInstanceViaNonAnnotatedConstructor.nonAnnotatedMethodInNonAnnotatedClass();
          s = NON_ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS;
          staticNonAnnotatedMethodInNonAnnotatedClass();

          s = NonAnnotatedClass.<error descr="'ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS' is scheduled for removal in version 123.456">ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS</error>;
          NonAnnotatedClass.<error descr="'staticAnnotatedMethodInNonAnnotatedClass()' is scheduled for removal in version 123.456">staticAnnotatedMethodInNonAnnotatedClass</error>();
          NonAnnotatedClass nonAnnotatedClassInstanceViaAnnotatedConstructor = new <error descr="'NonAnnotatedClass(java.lang.String)' is scheduled for removal in version 123.456">NonAnnotatedClass</error>("");
          s = nonAnnotatedClassInstanceViaAnnotatedConstructor.<error descr="'annotatedFieldInNonAnnotatedClass' is scheduled for removal in version 123.456">annotatedFieldInNonAnnotatedClass</error>;
          nonAnnotatedClassInstanceViaAnnotatedConstructor.<error descr="'annotatedMethodInNonAnnotatedClass()' is scheduled for removal in version 123.456">annotatedMethodInNonAnnotatedClass</error>();
          s = <error descr="'ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS' is scheduled for removal in version 123.456">ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS</error>;
          <error descr="'staticAnnotatedMethodInNonAnnotatedClass()' is scheduled for removal in version 123.456">staticAnnotatedMethodInNonAnnotatedClass</error>();

          // ---------------------------------

          <error descr="'scheduledForRemoval.pkg.AnnotatedEnum' is scheduled for removal in version 123.456">AnnotatedEnum</error> nonAnnotatedValueInAnnotatedEnum = <error descr="'scheduledForRemoval.pkg.AnnotatedEnum' is scheduled for removal in version 123.456">AnnotatedEnum</error>.<error descr="'NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is declared in enum 'scheduledForRemoval.pkg.AnnotatedEnum' scheduled for removal in version 123.456">NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM</error>;
          nonAnnotatedValueInAnnotatedEnum = <error descr="'NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is declared in enum 'scheduledForRemoval.pkg.AnnotatedEnum' scheduled for removal in version 123.456">NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM</error>;
          <error descr="'scheduledForRemoval.pkg.AnnotatedEnum' is scheduled for removal in version 123.456">AnnotatedEnum</error> annotatedValueInAnnotatedEnum = <error descr="'scheduledForRemoval.pkg.AnnotatedEnum' is scheduled for removal in version 123.456">AnnotatedEnum</error>.<error descr="'ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is scheduled for removal in version 123.456">ANNOTATED_VALUE_IN_ANNOTATED_ENUM</error>;
          annotatedValueInAnnotatedEnum = <error descr="'ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is scheduled for removal in version 123.456">ANNOTATED_VALUE_IN_ANNOTATED_ENUM</error>;

          NonAnnotatedEnum nonAnnotatedValueInNonAnnotatedEnum = NonAnnotatedEnum.NON_ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM;
          nonAnnotatedValueInNonAnnotatedEnum = NON_ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM;
          NonAnnotatedEnum annotatedValueInNonAnnotatedEnum = NonAnnotatedEnum.<error descr="'ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM' is scheduled for removal in version 123.456">ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM</error>;
          annotatedValueInNonAnnotatedEnum = <error descr="'ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM' is scheduled for removal in version 123.456">ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM</error>;
          
          // ---------------------------------

          @<error descr="'scheduledForRemoval.pkg.AnnotatedAnnotation' is scheduled for removal in version 123.456">AnnotatedAnnotation</error> class C1 {}
          @<error descr="'scheduledForRemoval.pkg.AnnotatedAnnotation' is scheduled for removal in version 123.456">AnnotatedAnnotation</error>(<error descr="'nonAnnotatedAttributeInAnnotatedAnnotation' is declared in annotation 'scheduledForRemoval.pkg.AnnotatedAnnotation' scheduled for removal in version 123.456">nonAnnotatedAttributeInAnnotatedAnnotation</error> = "123") class C2 {}
          @<error descr="'scheduledForRemoval.pkg.AnnotatedAnnotation' is scheduled for removal in version 123.456">AnnotatedAnnotation</error>(<error descr="'annotatedAttributeInAnnotatedAnnotation' is scheduled for removal in version 123.456">annotatedAttributeInAnnotatedAnnotation</error> = "123") class C3 {}
          @NonAnnotatedAnnotation class C4 {}
          @NonAnnotatedAnnotation(nonAnnotatedAttributeInNonAnnotatedAnnotation = "123") class C5 {}
          @NonAnnotatedAnnotation(<error descr="'annotatedAttributeInNonAnnotatedAnnotation' is scheduled for removal in version 123.456">annotatedAttributeInNonAnnotatedAnnotation</error> = "123") class C6 {}
        }
      }

      class DirectOverrideAnnotatedMethod extends NonAnnotatedClass {
        @Override
        public void <error descr="Overridden method 'annotatedMethodInNonAnnotatedClass()' is scheduled for removal in version 123.456">annotatedMethodInNonAnnotatedClass</error>() {}
      }

      class IndirectOverrideAnnotatedMethod extends DirectOverrideAnnotatedMethod {
        @Override
        public void annotatedMethodInNonAnnotatedClass() {}
      }

      class WarningsOfScheduledForRemovalTypesInSignature {
        public void classUsage() {
          new <error descr="'scheduledForRemoval.pkg.ClassWithScheduledForRemovalTypeInSignature' is scheduled for removal because its signature references class 'scheduledForRemoval.pkg.AnnotatedClass' scheduled for removal in version 123.456">ClassWithScheduledForRemovalTypeInSignature<<error descr="'scheduledForRemoval.pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</error>></error>();
        }

        public void membersUsages(OwnerOfMembersWithScheduledForRemovalTypesInSignature owner) {
          Object field = owner.<error descr="'field' is scheduled for removal because its signature references class 'scheduledForRemoval.pkg.AnnotatedClass' scheduled for removal in version 123.456">field</error>;
          owner.<error descr="'parameterType(scheduledForRemoval.pkg.AnnotatedClass)' is scheduled for removal because its signature references class 'scheduledForRemoval.pkg.AnnotatedClass' scheduled for removal in version 123.456">parameterType</error>(null);
          owner.<error descr="'returnType()' is scheduled for removal because its signature references class 'scheduledForRemoval.pkg.AnnotatedClass' scheduled for removal in version 123.456">returnType</error>();

          Object fieldPkg = owner.<error descr="'field' is scheduled for removal because its signature references class 'scheduledForRemoval.pkg.AnnotatedClass' scheduled for removal in version 123.456">field</error>;
          owner.<error descr="'parameterTypePkg(scheduledForRemoval.annotatedPkg.ClassInAnnotatedPkg)' is scheduled for removal because its signature references class 'scheduledForRemoval.annotatedPkg.ClassInAnnotatedPkg' scheduled for removal in version 123.456">parameterTypePkg</error>(null);
          owner.<error descr="'returnTypePkg()' is scheduled for removal because its signature references class 'scheduledForRemoval.pkg.AnnotatedClass' scheduled for removal in version 123.456">returnTypePkg</error>();
        }
      }
    """.trimIndent())
  }

  fun `test scheduled for removal fix`() {
    inspection.myIgnoreInsideImports = false
    inspection.myIgnoreApiDeclaredInThisProject = false
    myFixture.testQuickFix(JvmLanguage.JAVA, before = """
      package org.jetbrains.annotations;

      final class ApiStatus {
        public @interface ScheduledForRemoval { }
      }

      class X {
        /**
         * @deprecated use {@link #bar()}
         */
        @Deprecated
        @ApiStatus.ScheduledForRemoval
        public static void foo() { }
        public static void bar() { }
      }
      class Use {
        void test() {
          X.<caret>foo();
        }
      }
    """.trimIndent(), after = """
      package org.jetbrains.annotations;

      final class ApiStatus {
        public @interface ScheduledForRemoval { }
      }

      class X {
        /**
         * @deprecated use {@link #bar()}
         */
        @Deprecated
        @ApiStatus.ScheduledForRemoval
        public static void foo() { }
        public static void bar() { }
      }
      class Use {
        void test() {
          X.bar();
        }
      }
    """.trimIndent(), hint = "Replace method call with 'X.bar()'", testPreview = true)
  }
}