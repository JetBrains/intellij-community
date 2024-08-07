// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.tests.kotlin

import com.intellij.jvm.analysis.internal.testFramework.UnstableApiUsageInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider

abstract class KotlinUnstableApiUsageInspectionTest : UnstableApiUsageInspectionTestBase(), KotlinPluginModeProvider {
  fun `test kotlin unstable api usages`() {
    inspection.myIgnoreInsideImports = false
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      @file:Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "UNUSED_VALUE", "UNUSED_PARAMETER", "UNUSED_VARIABLE")

      import experimental.pkg.<warning descr="'experimental.pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning>
      import experimental.pkg.<warning descr="'experimental.pkg.ClassWithExperimentalTypeInSignature' is unstable because its signature references unstable class 'experimental.pkg.AnnotatedClass' marked with @ApiStatus.Experimental">ClassWithExperimentalTypeInSignature</warning>
      import experimental.pkg.OwnerOfMembersWithExperimentalTypesInSignature
      import experimental.pkg.<warning descr="'experimental.pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning>.<warning descr="'NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is declared in unstable class 'experimental.pkg.AnnotatedClass' marked with @ApiStatus.Experimental">NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</warning>
      import experimental.pkg.<warning descr="'experimental.pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning>.<warning descr="'staticNonAnnotatedMethodInAnnotatedClass()' is declared in unstable class 'experimental.pkg.AnnotatedClass' marked with @ApiStatus.Experimental">staticNonAnnotatedMethodInAnnotatedClass</warning>
      import experimental.pkg.<warning descr="'experimental.pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning>.<warning descr="'ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is marked unstable with @ApiStatus.Experimental">ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</warning>
      import experimental.pkg.<warning descr="'experimental.pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning>.<warning descr="'staticAnnotatedMethodInAnnotatedClass()' is marked unstable with @ApiStatus.Experimental">staticAnnotatedMethodInAnnotatedClass</warning>

      import experimental.pkg.NonAnnotatedClass 
      import experimental.pkg.NonAnnotatedClass.NON_ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS 
      import experimental.pkg.NonAnnotatedClass.staticNonAnnotatedMethodInNonAnnotatedClass 
      import experimental.pkg.NonAnnotatedClass.<warning descr="'ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS' is marked unstable with @ApiStatus.Experimental">ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS</warning>
      import experimental.pkg.NonAnnotatedClass.<warning descr="'staticAnnotatedMethodInNonAnnotatedClass()' is marked unstable with @ApiStatus.Experimental">staticAnnotatedMethodInNonAnnotatedClass</warning>

      import experimental.pkg.<warning descr="'experimental.pkg.AnnotatedEnum' is marked unstable with @ApiStatus.Experimental">AnnotatedEnum</warning>
      import experimental.pkg.NonAnnotatedEnum 
      import experimental.pkg.<warning descr="'experimental.pkg.AnnotatedEnum' is marked unstable with @ApiStatus.Experimental">AnnotatedEnum</warning>.<warning descr="'NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is declared in unstable enum 'experimental.pkg.AnnotatedEnum' marked with @ApiStatus.Experimental">NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM</warning>
      import experimental.pkg.<warning descr="'experimental.pkg.AnnotatedEnum' is marked unstable with @ApiStatus.Experimental">AnnotatedEnum</warning>.<warning descr="'ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is marked unstable with @ApiStatus.Experimental">ANNOTATED_VALUE_IN_ANNOTATED_ENUM</warning>
      import experimental.pkg.NonAnnotatedEnum.NON_ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM 
      import experimental.pkg.NonAnnotatedEnum.<warning descr="'ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM' is marked unstable with @ApiStatus.Experimental">ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM</warning>

      import experimental.pkg.<warning descr="'experimental.pkg.AnnotatedAnnotation' is marked unstable with @ApiStatus.Experimental">AnnotatedAnnotation</warning>
      import experimental.pkg.NonAnnotatedAnnotation

      import experimental.<warning descr="'experimental.annotatedPkg' is marked unstable with @ApiStatus.Experimental">annotatedPkg</warning>.<warning descr="'experimental.annotatedPkg.ClassInAnnotatedPkg' is declared in unstable package 'experimental.annotatedPkg' marked with @ApiStatus.Experimental">ClassInAnnotatedPkg</warning>

      class UnstableElementsTest {
        fun test() {
          var s = <warning descr="'experimental.pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning>.<warning descr="'NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is declared in unstable class 'experimental.pkg.AnnotatedClass' marked with @ApiStatus.Experimental">NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</warning>
          <warning descr="'experimental.pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning>.<warning descr="'staticNonAnnotatedMethodInAnnotatedClass()' is declared in unstable class 'experimental.pkg.AnnotatedClass' marked with @ApiStatus.Experimental">staticNonAnnotatedMethodInAnnotatedClass</warning>()
          val annotatedClassInstanceViaNonAnnotatedConstructor : <warning descr="'experimental.pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning> = <warning descr="'AnnotatedClass()' is declared in unstable class 'experimental.pkg.AnnotatedClass' marked with @ApiStatus.Experimental"><warning descr="'experimental.pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning></warning>()
          s = annotatedClassInstanceViaNonAnnotatedConstructor.<warning descr="'nonAnnotatedFieldInAnnotatedClass' is declared in unstable class 'experimental.pkg.AnnotatedClass' marked with @ApiStatus.Experimental">nonAnnotatedFieldInAnnotatedClass</warning>
          annotatedClassInstanceViaNonAnnotatedConstructor.<warning descr="'nonAnnotatedMethodInAnnotatedClass()' is declared in unstable class 'experimental.pkg.AnnotatedClass' marked with @ApiStatus.Experimental">nonAnnotatedMethodInAnnotatedClass</warning>()
          s = <warning descr="'NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is declared in unstable class 'experimental.pkg.AnnotatedClass' marked with @ApiStatus.Experimental">NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</warning>
          <warning descr="'staticNonAnnotatedMethodInAnnotatedClass()' is declared in unstable class 'experimental.pkg.AnnotatedClass' marked with @ApiStatus.Experimental">staticNonAnnotatedMethodInAnnotatedClass</warning>()

          s = <warning descr="'experimental.pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning>.<warning descr="'ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is marked unstable with @ApiStatus.Experimental">ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</warning>
          <warning descr="'experimental.pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning>.<warning descr="'staticAnnotatedMethodInAnnotatedClass()' is marked unstable with @ApiStatus.Experimental">staticAnnotatedMethodInAnnotatedClass</warning>()
          val annotatedClassInstanceViaAnnotatedConstructor : <warning descr="'experimental.pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning> = <warning descr="'AnnotatedClass(java.lang.String)' is marked unstable with @ApiStatus.Experimental"><warning descr="'experimental.pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning></warning>("")
          s = annotatedClassInstanceViaAnnotatedConstructor.<warning descr="'annotatedFieldInAnnotatedClass' is marked unstable with @ApiStatus.Experimental">annotatedFieldInAnnotatedClass</warning>
          annotatedClassInstanceViaAnnotatedConstructor.<warning descr="'annotatedMethodInAnnotatedClass()' is marked unstable with @ApiStatus.Experimental">annotatedMethodInAnnotatedClass</warning>()
          s = <warning descr="'ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is marked unstable with @ApiStatus.Experimental">ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</warning>
          <warning descr="'staticAnnotatedMethodInAnnotatedClass()' is marked unstable with @ApiStatus.Experimental">staticAnnotatedMethodInAnnotatedClass</warning>()

          // ---------------------------------

          s = NonAnnotatedClass.NON_ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS 
          NonAnnotatedClass.staticNonAnnotatedMethodInNonAnnotatedClass() 
          val nonAnnotatedClassInstanceViaNonAnnotatedConstructor = NonAnnotatedClass() 
          s = nonAnnotatedClassInstanceViaNonAnnotatedConstructor.nonAnnotatedFieldInNonAnnotatedClass 
          nonAnnotatedClassInstanceViaNonAnnotatedConstructor.nonAnnotatedMethodInNonAnnotatedClass() 
          s = NON_ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS 
          staticNonAnnotatedMethodInNonAnnotatedClass() 

          s = NonAnnotatedClass.<warning descr="'ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS' is marked unstable with @ApiStatus.Experimental">ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS</warning>
          NonAnnotatedClass.<warning descr="'staticAnnotatedMethodInNonAnnotatedClass()' is marked unstable with @ApiStatus.Experimental">staticAnnotatedMethodInNonAnnotatedClass</warning>()
          val nonAnnotatedClassInstanceViaAnnotatedConstructor = <warning descr="'NonAnnotatedClass(java.lang.String)' is marked unstable with @ApiStatus.Experimental">NonAnnotatedClass</warning>("")
          s = nonAnnotatedClassInstanceViaAnnotatedConstructor.<warning descr="'annotatedFieldInNonAnnotatedClass' is marked unstable with @ApiStatus.Experimental">annotatedFieldInNonAnnotatedClass</warning>
          nonAnnotatedClassInstanceViaAnnotatedConstructor.<warning descr="'annotatedMethodInNonAnnotatedClass()' is marked unstable with @ApiStatus.Experimental">annotatedMethodInNonAnnotatedClass</warning>()
          s = <warning descr="'ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS' is marked unstable with @ApiStatus.Experimental">ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS</warning>
          <warning descr="'staticAnnotatedMethodInNonAnnotatedClass()' is marked unstable with @ApiStatus.Experimental">staticAnnotatedMethodInNonAnnotatedClass</warning>()

          // ---------------------------------

          var nonAnnotatedValueInAnnotatedEnum : <warning descr="'experimental.pkg.AnnotatedEnum' is marked unstable with @ApiStatus.Experimental">AnnotatedEnum</warning> = <warning descr="'experimental.pkg.AnnotatedEnum' is marked unstable with @ApiStatus.Experimental">AnnotatedEnum</warning>.<warning descr="'NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is declared in unstable enum 'experimental.pkg.AnnotatedEnum' marked with @ApiStatus.Experimental">NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM</warning>
          nonAnnotatedValueInAnnotatedEnum = <warning descr="'NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is declared in unstable enum 'experimental.pkg.AnnotatedEnum' marked with @ApiStatus.Experimental">NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM</warning>
          var annotatedValueInAnnotatedEnum : <warning descr="'experimental.pkg.AnnotatedEnum' is marked unstable with @ApiStatus.Experimental">AnnotatedEnum</warning> = <warning descr="'experimental.pkg.AnnotatedEnum' is marked unstable with @ApiStatus.Experimental">AnnotatedEnum</warning>.<warning descr="'ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is marked unstable with @ApiStatus.Experimental">ANNOTATED_VALUE_IN_ANNOTATED_ENUM</warning>
          annotatedValueInAnnotatedEnum = <warning descr="'ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is marked unstable with @ApiStatus.Experimental">ANNOTATED_VALUE_IN_ANNOTATED_ENUM</warning>

          var nonAnnotatedValueInNonAnnotatedEnum = NonAnnotatedEnum.NON_ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM 
          nonAnnotatedValueInNonAnnotatedEnum = NON_ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM 
          var annotatedValueInNonAnnotatedEnum = NonAnnotatedEnum.<warning descr="'ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM' is marked unstable with @ApiStatus.Experimental">ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM</warning>
          annotatedValueInNonAnnotatedEnum = <warning descr="'ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM' is marked unstable with @ApiStatus.Experimental">ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM</warning>

          // ---------------------------------

          @<warning descr="'experimental.pkg.AnnotatedAnnotation' is marked unstable with @ApiStatus.Experimental">AnnotatedAnnotation</warning> class C1
          @<warning descr="'experimental.pkg.AnnotatedAnnotation' is marked unstable with @ApiStatus.Experimental">AnnotatedAnnotation</warning>(<warning descr="'nonAnnotatedAttributeInAnnotatedAnnotation' is declared in unstable annotation 'experimental.pkg.AnnotatedAnnotation' marked with @ApiStatus.Experimental">nonAnnotatedAttributeInAnnotatedAnnotation</warning> = "123") class C2
          @<warning descr="'experimental.pkg.AnnotatedAnnotation' is marked unstable with @ApiStatus.Experimental">AnnotatedAnnotation</warning>(<warning descr="'annotatedAttributeInAnnotatedAnnotation' is marked unstable with @ApiStatus.Experimental">annotatedAttributeInAnnotatedAnnotation</warning> = "123") class C3
          @NonAnnotatedAnnotation class C4 
          @NonAnnotatedAnnotation(nonAnnotatedAttributeInNonAnnotatedAnnotation = "123") class C5 
          @NonAnnotatedAnnotation(<warning descr="'annotatedAttributeInNonAnnotatedAnnotation' is marked unstable with @ApiStatus.Experimental">annotatedAttributeInNonAnnotatedAnnotation</warning> = "123") class C6
        }
      }

      open class DirectOverrideAnnotatedMethod : NonAnnotatedClass() {
        override fun <warning descr="Overridden method 'annotatedMethodInNonAnnotatedClass()' is marked unstable with @ApiStatus.Experimental">annotatedMethodInNonAnnotatedClass</warning>() {}
      }

      //No warning should be produced.
      class IndirectOverrideAnnotatedMethod : DirectOverrideAnnotatedMethod() {
        override fun annotatedMethodInNonAnnotatedClass() {}
      }

      class DirectOverrideNonAnnotatedMethodInAnnotatedClass : <warning descr="'AnnotatedClass()' is declared in unstable class 'experimental.pkg.AnnotatedClass' marked with @ApiStatus.Experimental"><warning descr="'experimental.pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning></warning>() {
        override fun <warning descr="Overridden method 'nonAnnotatedMethodInAnnotatedClass()' is declared in unstable class 'experimental.pkg.AnnotatedClass' marked with @ApiStatus.Experimental">nonAnnotatedMethodInAnnotatedClass</warning>() {}
      }

      class DirectOverrideAnnotatedMethodInAnnotatedClass : <warning descr="'AnnotatedClass()' is declared in unstable class 'experimental.pkg.AnnotatedClass' marked with @ApiStatus.Experimental"><warning descr="'experimental.pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning></warning>() {
        override fun <warning descr="Overridden method 'annotatedMethodInAnnotatedClass()' is marked unstable with @ApiStatus.Experimental">annotatedMethodInAnnotatedClass</warning>() {}
      }

      class WarningsOfExperimentalTypesInSignature {
        fun classUsage() {
          experimental.pkg.<warning descr="'experimental.pkg.ClassWithExperimentalTypeInSignature' is unstable because its signature references unstable class 'experimental.pkg.AnnotatedClass' marked with @ApiStatus.Experimental">ClassWithExperimentalTypeInSignature</warning><<warning descr="'experimental.pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning>>()
        }

        fun membersUsages(owner: OwnerOfMembersWithExperimentalTypesInSignature) {
          val field = owner.<warning descr="'field' is unstable because its signature references unstable class 'experimental.pkg.AnnotatedClass' marked with @ApiStatus.Experimental">field</warning>
          owner.<warning descr="'parameterType(experimental.pkg.AnnotatedClass)' is unstable because its signature references unstable class 'experimental.pkg.AnnotatedClass' marked with @ApiStatus.Experimental">parameterType</warning>(null)
          owner.<warning descr="'returnType()' is unstable because its signature references unstable class 'experimental.pkg.AnnotatedClass' marked with @ApiStatus.Experimental">returnType</warning>()

          val fieldPkg = owner.<warning descr="'field' is unstable because its signature references unstable class 'experimental.pkg.AnnotatedClass' marked with @ApiStatus.Experimental">field</warning>
          owner.<warning descr="'parameterTypePkg(experimental.annotatedPkg.ClassInAnnotatedPkg)' is unstable because its signature references unstable class 'experimental.annotatedPkg.ClassInAnnotatedPkg' marked with @ApiStatus.Experimental">parameterTypePkg</warning>(null)
          owner.<warning descr="'returnTypePkg()' is unstable because its signature references unstable class 'experimental.pkg.AnnotatedClass' marked with @ApiStatus.Experimental">returnTypePkg</warning>()
        }
      }
    """.trimIndent())
  }

  fun `test kotlin no warnings on access to members of the same file`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      @file:Suppress("UNUSED_PARAMETER")

      package test;

      import experimental.pkg.AnnotatedClass;

      class NoWarningsMembersOfTheSameFile {
        companion object {
          private var staticField: <warning descr="'experimental.pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning>? = null;
          private fun staticReturnType(): <warning descr="'experimental.pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning>? { return null; }
          private fun staticParamType(param: <warning descr="'experimental.pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning>?) { }
        }
        private var field: <warning descr="'experimental.pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning>? = null;
        private fun returnType(): <warning descr="'experimental.pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning>? { return null; }
        private fun paramType(param: <warning descr="'experimental.pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning>?) { }

        fun testNoWarningsProducedForMembersOfTheSameClass() {
          field?.toString();
          staticField?.toString();
          returnType();
          paramType(null);
          staticReturnType();
          staticParamType(null)
        }

        private inner class InnerClass {
          fun testNoWarningsProducedForMembersEnclosingClass() {
            field.toString();
            staticField.toString();
            returnType();
            paramType(null);
            staticReturnType();
            staticParamType(null)
          }
        }
      }
    """.trimIndent())
  }

  fun `test kotlin do not report unstable api usages inside import statements`() {
    inspection.myIgnoreInsideImports = true
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import experimental.pkg.AnnotatedClass
      import experimental.pkg.AnnotatedClass.NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS
      import experimental.pkg.AnnotatedClass.staticNonAnnotatedMethodInAnnotatedClass
      import experimental.pkg.AnnotatedClass.ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS
      import experimental.pkg.AnnotatedClass.staticAnnotatedMethodInAnnotatedClass

      import experimental.pkg.NonAnnotatedClass 
      import experimental.pkg.NonAnnotatedClass.NON_ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS 
      import experimental.pkg.NonAnnotatedClass.staticNonAnnotatedMethodInNonAnnotatedClass 
      import experimental.pkg.NonAnnotatedClass.ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS
      import experimental.pkg.NonAnnotatedClass.staticAnnotatedMethodInNonAnnotatedClass

      import experimental.pkg.AnnotatedEnum
      import experimental.pkg.NonAnnotatedEnum 
      import experimental.pkg.AnnotatedEnum.NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM
      import experimental.pkg.AnnotatedEnum.ANNOTATED_VALUE_IN_ANNOTATED_ENUM
      import experimental.pkg.NonAnnotatedEnum.NON_ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM 
      import experimental.pkg.NonAnnotatedEnum.ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM

      import experimental.pkg.AnnotatedAnnotation
      import experimental.pkg.NonAnnotatedAnnotation

      import experimental.annotatedPkg.ClassInAnnotatedPkg
    """.trimIndent())
  }

  fun `test Kotlin scheduled for removal`() {
    inspection.myIgnoreInsideImports = false
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      @file:Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "UNUSED_VALUE", "UNUSED_PARAMETER", "UNUSED_VARIABLE")

      import scheduledForRemoval.pkg.<error descr="'scheduledForRemoval.pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</error>
      import scheduledForRemoval.pkg.<error descr="'scheduledForRemoval.pkg.ClassWithScheduledForRemovalTypeInSignature' is scheduled for removal because its signature references class 'scheduledForRemoval.pkg.AnnotatedClass' scheduled for removal in version 123.456">ClassWithScheduledForRemovalTypeInSignature</error>
      import scheduledForRemoval.pkg.OwnerOfMembersWithScheduledForRemovalTypesInSignature
      import scheduledForRemoval.pkg.<error descr="'scheduledForRemoval.pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</error>.<error descr="'NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is declared in class 'scheduledForRemoval.pkg.AnnotatedClass' scheduled for removal in version 123.456">NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</error>
      import scheduledForRemoval.pkg.<error descr="'scheduledForRemoval.pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</error>.<error descr="'staticNonAnnotatedMethodInAnnotatedClass()' is declared in class 'scheduledForRemoval.pkg.AnnotatedClass' scheduled for removal in version 123.456">staticNonAnnotatedMethodInAnnotatedClass</error>
      import scheduledForRemoval.pkg.<error descr="'scheduledForRemoval.pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</error>.<error descr="'ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is scheduled for removal in version 123.456">ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</error>
      import scheduledForRemoval.pkg.<error descr="'scheduledForRemoval.pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</error>.<error descr="'staticAnnotatedMethodInAnnotatedClass()' is scheduled for removal in version 123.456">staticAnnotatedMethodInAnnotatedClass</error>

      import scheduledForRemoval.pkg.NonAnnotatedClass 
      import scheduledForRemoval.pkg.NonAnnotatedClass.NON_ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS 
      import scheduledForRemoval.pkg.NonAnnotatedClass.staticNonAnnotatedMethodInNonAnnotatedClass 
      import scheduledForRemoval.pkg.NonAnnotatedClass.<error descr="'ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS' is scheduled for removal in version 123.456">ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS</error>
      import scheduledForRemoval.pkg.NonAnnotatedClass.<error descr="'staticAnnotatedMethodInNonAnnotatedClass()' is scheduled for removal in version 123.456">staticAnnotatedMethodInNonAnnotatedClass</error>

      import scheduledForRemoval.pkg.<error descr="'scheduledForRemoval.pkg.AnnotatedEnum' is scheduled for removal in version 123.456">AnnotatedEnum</error>
      import scheduledForRemoval.pkg.NonAnnotatedEnum 
      import scheduledForRemoval.pkg.<error descr="'scheduledForRemoval.pkg.AnnotatedEnum' is scheduled for removal in version 123.456">AnnotatedEnum</error>.<error descr="'NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is declared in enum 'scheduledForRemoval.pkg.AnnotatedEnum' scheduled for removal in version 123.456">NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM</error>
      import scheduledForRemoval.pkg.<error descr="'scheduledForRemoval.pkg.AnnotatedEnum' is scheduled for removal in version 123.456">AnnotatedEnum</error>.<error descr="'ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is scheduled for removal in version 123.456">ANNOTATED_VALUE_IN_ANNOTATED_ENUM</error>
      import scheduledForRemoval.pkg.NonAnnotatedEnum.NON_ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM 
      import scheduledForRemoval.pkg.NonAnnotatedEnum.<error descr="'ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM' is scheduled for removal in version 123.456">ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM</error>

      import scheduledForRemoval.pkg.<error descr="'scheduledForRemoval.pkg.AnnotatedAnnotation' is scheduled for removal in version 123.456">AnnotatedAnnotation</error>
      import scheduledForRemoval.pkg.NonAnnotatedAnnotation

      import scheduledForRemoval.<error descr="'scheduledForRemoval.annotatedPkg' is scheduled for removal in version 123.456">annotatedPkg</error>.<error descr="'scheduledForRemoval.annotatedPkg.ClassInAnnotatedPkg' is declared in package 'scheduledForRemoval.annotatedPkg' scheduled for removal in version 123.456">ClassInAnnotatedPkg</error>

      @Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "UNUSED_VALUE")
      class ScheduledForRemovalElementsTest {
        fun test() {
          var s = <error descr="'scheduledForRemoval.pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</error>.<error descr="'NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is declared in class 'scheduledForRemoval.pkg.AnnotatedClass' scheduled for removal in version 123.456">NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</error>
          <error descr="'scheduledForRemoval.pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</error>.<error descr="'staticNonAnnotatedMethodInAnnotatedClass()' is declared in class 'scheduledForRemoval.pkg.AnnotatedClass' scheduled for removal in version 123.456">staticNonAnnotatedMethodInAnnotatedClass</error>()
          val annotatedClassInstanceViaNonAnnotatedConstructor : <error descr="'scheduledForRemoval.pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</error> = <error descr="'AnnotatedClass()' is declared in class 'scheduledForRemoval.pkg.AnnotatedClass' scheduled for removal in version 123.456"><error descr="'scheduledForRemoval.pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</error></error>()
          s = annotatedClassInstanceViaNonAnnotatedConstructor.<error descr="'nonAnnotatedFieldInAnnotatedClass' is declared in class 'scheduledForRemoval.pkg.AnnotatedClass' scheduled for removal in version 123.456">nonAnnotatedFieldInAnnotatedClass</error>
          annotatedClassInstanceViaNonAnnotatedConstructor.<error descr="'nonAnnotatedMethodInAnnotatedClass()' is declared in class 'scheduledForRemoval.pkg.AnnotatedClass' scheduled for removal in version 123.456">nonAnnotatedMethodInAnnotatedClass</error>()
          s = <error descr="'NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is declared in class 'scheduledForRemoval.pkg.AnnotatedClass' scheduled for removal in version 123.456">NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</error>
          <error descr="'staticNonAnnotatedMethodInAnnotatedClass()' is declared in class 'scheduledForRemoval.pkg.AnnotatedClass' scheduled for removal in version 123.456">staticNonAnnotatedMethodInAnnotatedClass</error>()

          s = <error descr="'scheduledForRemoval.pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</error>.<error descr="'ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is scheduled for removal in version 123.456">ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</error>
          <error descr="'scheduledForRemoval.pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</error>.<error descr="'staticAnnotatedMethodInAnnotatedClass()' is scheduled for removal in version 123.456">staticAnnotatedMethodInAnnotatedClass</error>()
          val annotatedClassInstanceViaAnnotatedConstructor : <error descr="'scheduledForRemoval.pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</error> = <error descr="'AnnotatedClass(java.lang.String)' is scheduled for removal in version 123.456"><error descr="'scheduledForRemoval.pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</error></error>("")
          s = annotatedClassInstanceViaAnnotatedConstructor.<error descr="'annotatedFieldInAnnotatedClass' is scheduled for removal in version 123.456">annotatedFieldInAnnotatedClass</error>
          annotatedClassInstanceViaAnnotatedConstructor.<error descr="'annotatedMethodInAnnotatedClass()' is scheduled for removal in version 123.456">annotatedMethodInAnnotatedClass</error>()
          s = <error descr="'ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is scheduled for removal in version 123.456">ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</error>
          <error descr="'staticAnnotatedMethodInAnnotatedClass()' is scheduled for removal in version 123.456">staticAnnotatedMethodInAnnotatedClass</error>()

          // ---------------------------------

          s = NonAnnotatedClass.NON_ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS 
          NonAnnotatedClass.staticNonAnnotatedMethodInNonAnnotatedClass() 
          val nonAnnotatedClassInstanceViaNonAnnotatedConstructor = NonAnnotatedClass() 
          s = nonAnnotatedClassInstanceViaNonAnnotatedConstructor.nonAnnotatedFieldInNonAnnotatedClass 
          nonAnnotatedClassInstanceViaNonAnnotatedConstructor.nonAnnotatedMethodInNonAnnotatedClass() 
          s = NON_ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS 
          staticNonAnnotatedMethodInNonAnnotatedClass() 

          s = NonAnnotatedClass.<error descr="'ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS' is scheduled for removal in version 123.456">ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS</error>
          NonAnnotatedClass.<error descr="'staticAnnotatedMethodInNonAnnotatedClass()' is scheduled for removal in version 123.456">staticAnnotatedMethodInNonAnnotatedClass</error>()
          val nonAnnotatedClassInstanceViaAnnotatedConstructor = <error descr="'NonAnnotatedClass(java.lang.String)' is scheduled for removal in version 123.456">NonAnnotatedClass</error>("")
          s = nonAnnotatedClassInstanceViaAnnotatedConstructor.<error descr="'annotatedFieldInNonAnnotatedClass' is scheduled for removal in version 123.456">annotatedFieldInNonAnnotatedClass</error>
          nonAnnotatedClassInstanceViaAnnotatedConstructor.<error descr="'annotatedMethodInNonAnnotatedClass()' is scheduled for removal in version 123.456">annotatedMethodInNonAnnotatedClass</error>()
          s = <error descr="'ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS' is scheduled for removal in version 123.456">ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS</error>
          <error descr="'staticAnnotatedMethodInNonAnnotatedClass()' is scheduled for removal in version 123.456">staticAnnotatedMethodInNonAnnotatedClass</error>()

          // ---------------------------------

          var nonAnnotatedValueInAnnotatedEnum : <error descr="'scheduledForRemoval.pkg.AnnotatedEnum' is scheduled for removal in version 123.456">AnnotatedEnum</error> = <error descr="'scheduledForRemoval.pkg.AnnotatedEnum' is scheduled for removal in version 123.456">AnnotatedEnum</error>.<error descr="'NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is declared in enum 'scheduledForRemoval.pkg.AnnotatedEnum' scheduled for removal in version 123.456">NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM</error>
          nonAnnotatedValueInAnnotatedEnum = <error descr="'NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is declared in enum 'scheduledForRemoval.pkg.AnnotatedEnum' scheduled for removal in version 123.456">NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM</error>
          var annotatedValueInAnnotatedEnum : <error descr="'scheduledForRemoval.pkg.AnnotatedEnum' is scheduled for removal in version 123.456">AnnotatedEnum</error> = <error descr="'scheduledForRemoval.pkg.AnnotatedEnum' is scheduled for removal in version 123.456">AnnotatedEnum</error>.<error descr="'ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is scheduled for removal in version 123.456">ANNOTATED_VALUE_IN_ANNOTATED_ENUM</error>
          annotatedValueInAnnotatedEnum = <error descr="'ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is scheduled for removal in version 123.456">ANNOTATED_VALUE_IN_ANNOTATED_ENUM</error>

          var nonAnnotatedValueInNonAnnotatedEnum = NonAnnotatedEnum.NON_ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM 
          nonAnnotatedValueInNonAnnotatedEnum = NON_ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM 
          var annotatedValueInNonAnnotatedEnum = NonAnnotatedEnum.<error descr="'ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM' is scheduled for removal in version 123.456">ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM</error>
          annotatedValueInNonAnnotatedEnum = <error descr="'ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM' is scheduled for removal in version 123.456">ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM</error>

          // ---------------------------------

          @<error descr="'scheduledForRemoval.pkg.AnnotatedAnnotation' is scheduled for removal in version 123.456">AnnotatedAnnotation</error> class C1
          @<error descr="'scheduledForRemoval.pkg.AnnotatedAnnotation' is scheduled for removal in version 123.456">AnnotatedAnnotation</error>(<error descr="'nonAnnotatedAttributeInAnnotatedAnnotation' is declared in annotation 'scheduledForRemoval.pkg.AnnotatedAnnotation' scheduled for removal in version 123.456">nonAnnotatedAttributeInAnnotatedAnnotation</error> = "123") class C2
          @<error descr="'scheduledForRemoval.pkg.AnnotatedAnnotation' is scheduled for removal in version 123.456">AnnotatedAnnotation</error>(<error descr="'annotatedAttributeInAnnotatedAnnotation' is scheduled for removal in version 123.456">annotatedAttributeInAnnotatedAnnotation</error> = "123") class C3
          @NonAnnotatedAnnotation class C4 
          @NonAnnotatedAnnotation(nonAnnotatedAttributeInNonAnnotatedAnnotation = "123") class C5 
          @NonAnnotatedAnnotation(<error descr="'annotatedAttributeInNonAnnotatedAnnotation' is scheduled for removal in version 123.456">annotatedAttributeInNonAnnotatedAnnotation</error> = "123") class C6
        }
      }

      open class DirectOverrideAnnotatedMethod : NonAnnotatedClass() {
        override fun <error descr="Overridden method 'annotatedMethodInNonAnnotatedClass()' is scheduled for removal in version 123.456">annotatedMethodInNonAnnotatedClass</error>() {}
      }

      //No warning should be produced.
      class IndirectOverrideAnnotatedMethod : DirectOverrideAnnotatedMethod() {
        override fun annotatedMethodInNonAnnotatedClass() {}
      }

      class WarningsOfScheduledForRemovalTypesInSignature {
        fun classUsage() {
          <error descr="'scheduledForRemoval.pkg.ClassWithScheduledForRemovalTypeInSignature' is scheduled for removal because its signature references class 'scheduledForRemoval.pkg.AnnotatedClass' scheduled for removal in version 123.456">ClassWithScheduledForRemovalTypeInSignature</error><<error descr="'scheduledForRemoval.pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</error>>()
        }

        fun membersUsages(owner: OwnerOfMembersWithScheduledForRemovalTypesInSignature) {
          val field = owner.<error descr="'field' is scheduled for removal because its signature references class 'scheduledForRemoval.pkg.AnnotatedClass' scheduled for removal in version 123.456">field</error>
          owner.<error descr="'parameterType(scheduledForRemoval.pkg.AnnotatedClass)' is scheduled for removal because its signature references class 'scheduledForRemoval.pkg.AnnotatedClass' scheduled for removal in version 123.456">parameterType</error>(null)
          owner.<error descr="'returnType()' is scheduled for removal because its signature references class 'scheduledForRemoval.pkg.AnnotatedClass' scheduled for removal in version 123.456">returnType</error>()

          val fieldPkg = owner.<error descr="'field' is scheduled for removal because its signature references class 'scheduledForRemoval.pkg.AnnotatedClass' scheduled for removal in version 123.456">field</error>
          owner.<error descr="'parameterTypePkg(scheduledForRemoval.annotatedPkg.ClassInAnnotatedPkg)' is scheduled for removal because its signature references class 'scheduledForRemoval.annotatedPkg.ClassInAnnotatedPkg' scheduled for removal in version 123.456">parameterTypePkg</error>(null)
          owner.<error descr="'returnTypePkg()' is scheduled for removal because its signature references class 'scheduledForRemoval.pkg.AnnotatedClass' scheduled for removal in version 123.456">returnTypePkg</error>()
        }
      }
    """.trimIndent())
  }
}