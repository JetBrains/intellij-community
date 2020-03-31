@file:Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "UNUSED_VALUE", "UNUSED_PARAMETER", "UNUSED_VARIABLE")

import pkg.<warning descr="'pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning>
import pkg.<warning descr="'pkg.ClassWithExperimentalTypeInSignature' is unstable because its signature references unstable class 'pkg.AnnotatedClass' marked with @ApiStatus.Experimental">ClassWithExperimentalTypeInSignature</warning>
import pkg.OwnerOfMembersWithExperimentalTypesInSignature
import pkg.<warning descr="'pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning>.<warning descr="'NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is declared in unstable class 'pkg.AnnotatedClass' marked with @ApiStatus.Experimental">NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</warning>
import pkg.<warning descr="'pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning>.<warning descr="'staticNonAnnotatedMethodInAnnotatedClass()' is declared in unstable class 'pkg.AnnotatedClass' marked with @ApiStatus.Experimental">staticNonAnnotatedMethodInAnnotatedClass</warning>
import pkg.<warning descr="'pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning>.<warning descr="'ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is marked unstable with @ApiStatus.Experimental">ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</warning>
import pkg.<warning descr="'pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning>.<warning descr="'staticAnnotatedMethodInAnnotatedClass()' is marked unstable with @ApiStatus.Experimental">staticAnnotatedMethodInAnnotatedClass</warning>

import pkg.NonAnnotatedClass 
import pkg.NonAnnotatedClass.NON_ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS 
import pkg.NonAnnotatedClass.staticNonAnnotatedMethodInNonAnnotatedClass 
import pkg.NonAnnotatedClass.<warning descr="'ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS' is marked unstable with @ApiStatus.Experimental">ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS</warning>
import pkg.NonAnnotatedClass.<warning descr="'staticAnnotatedMethodInNonAnnotatedClass()' is marked unstable with @ApiStatus.Experimental">staticAnnotatedMethodInNonAnnotatedClass</warning>

import pkg.<warning descr="'pkg.AnnotatedEnum' is marked unstable with @ApiStatus.Experimental">AnnotatedEnum</warning>
import pkg.NonAnnotatedEnum 
import pkg.<warning descr="'pkg.AnnotatedEnum' is marked unstable with @ApiStatus.Experimental">AnnotatedEnum</warning>.<warning descr="'NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is declared in unstable enum 'pkg.AnnotatedEnum' marked with @ApiStatus.Experimental">NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM</warning>
import pkg.<warning descr="'pkg.AnnotatedEnum' is marked unstable with @ApiStatus.Experimental">AnnotatedEnum</warning>.<warning descr="'ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is marked unstable with @ApiStatus.Experimental">ANNOTATED_VALUE_IN_ANNOTATED_ENUM</warning>
import pkg.NonAnnotatedEnum.NON_ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM 
import pkg.NonAnnotatedEnum.<warning descr="'ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM' is marked unstable with @ApiStatus.Experimental">ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM</warning>

import pkg.<warning descr="'pkg.AnnotatedAnnotation' is marked unstable with @ApiStatus.Experimental">AnnotatedAnnotation</warning>
import pkg.NonAnnotatedAnnotation

import <warning descr="'annotatedPkg' is marked unstable with @ApiStatus.Experimental">annotatedPkg</warning>.<warning descr="'annotatedPkg.ClassInAnnotatedPkg' is declared in unstable package 'annotatedPkg' marked with @ApiStatus.Experimental">ClassInAnnotatedPkg</warning>

class UnstableElementsTest {
  fun test() {
    var s = <warning descr="'pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning>.<warning descr="'NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is declared in unstable class 'pkg.AnnotatedClass' marked with @ApiStatus.Experimental">NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</warning>
    <warning descr="'pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning>.<warning descr="'staticNonAnnotatedMethodInAnnotatedClass()' is declared in unstable class 'pkg.AnnotatedClass' marked with @ApiStatus.Experimental">staticNonAnnotatedMethodInAnnotatedClass</warning>()
    val annotatedClassInstanceViaNonAnnotatedConstructor : <warning descr="'pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning> = <warning descr="'AnnotatedClass()' is declared in unstable class 'pkg.AnnotatedClass' marked with @ApiStatus.Experimental"><warning descr="'pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning></warning>()
    s = annotatedClassInstanceViaNonAnnotatedConstructor.<warning descr="'nonAnnotatedFieldInAnnotatedClass' is declared in unstable class 'pkg.AnnotatedClass' marked with @ApiStatus.Experimental">nonAnnotatedFieldInAnnotatedClass</warning>
    annotatedClassInstanceViaNonAnnotatedConstructor.<warning descr="'nonAnnotatedMethodInAnnotatedClass()' is declared in unstable class 'pkg.AnnotatedClass' marked with @ApiStatus.Experimental">nonAnnotatedMethodInAnnotatedClass</warning>()
    s = <warning descr="'NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is declared in unstable class 'pkg.AnnotatedClass' marked with @ApiStatus.Experimental">NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</warning>
    <warning descr="'staticNonAnnotatedMethodInAnnotatedClass()' is declared in unstable class 'pkg.AnnotatedClass' marked with @ApiStatus.Experimental">staticNonAnnotatedMethodInAnnotatedClass</warning>()

    s = <warning descr="'pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning>.<warning descr="'ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is marked unstable with @ApiStatus.Experimental">ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</warning>
    <warning descr="'pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning>.<warning descr="'staticAnnotatedMethodInAnnotatedClass()' is marked unstable with @ApiStatus.Experimental">staticAnnotatedMethodInAnnotatedClass</warning>()
    val annotatedClassInstanceViaAnnotatedConstructor : <warning descr="'pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning> = <warning descr="'AnnotatedClass(java.lang.String)' is marked unstable with @ApiStatus.Experimental"><warning descr="'pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning></warning>("")
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

    var nonAnnotatedValueInAnnotatedEnum : <warning descr="'pkg.AnnotatedEnum' is marked unstable with @ApiStatus.Experimental">AnnotatedEnum</warning> = <warning descr="'pkg.AnnotatedEnum' is marked unstable with @ApiStatus.Experimental">AnnotatedEnum</warning>.<warning descr="'NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is declared in unstable enum 'pkg.AnnotatedEnum' marked with @ApiStatus.Experimental">NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM</warning>
    nonAnnotatedValueInAnnotatedEnum = <warning descr="'NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is declared in unstable enum 'pkg.AnnotatedEnum' marked with @ApiStatus.Experimental">NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM</warning>
    var annotatedValueInAnnotatedEnum : <warning descr="'pkg.AnnotatedEnum' is marked unstable with @ApiStatus.Experimental">AnnotatedEnum</warning> = <warning descr="'pkg.AnnotatedEnum' is marked unstable with @ApiStatus.Experimental">AnnotatedEnum</warning>.<warning descr="'ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is marked unstable with @ApiStatus.Experimental">ANNOTATED_VALUE_IN_ANNOTATED_ENUM</warning>
    annotatedValueInAnnotatedEnum = <warning descr="'ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is marked unstable with @ApiStatus.Experimental">ANNOTATED_VALUE_IN_ANNOTATED_ENUM</warning>

    var nonAnnotatedValueInNonAnnotatedEnum = NonAnnotatedEnum.NON_ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM 
    nonAnnotatedValueInNonAnnotatedEnum = NON_ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM 
    var annotatedValueInNonAnnotatedEnum = NonAnnotatedEnum.<warning descr="'ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM' is marked unstable with @ApiStatus.Experimental">ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM</warning>
    annotatedValueInNonAnnotatedEnum = <warning descr="'ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM' is marked unstable with @ApiStatus.Experimental">ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM</warning>

    // ---------------------------------

    @<warning descr="'pkg.AnnotatedAnnotation' is marked unstable with @ApiStatus.Experimental">AnnotatedAnnotation</warning> class C1
    @<warning descr="'pkg.AnnotatedAnnotation' is marked unstable with @ApiStatus.Experimental">AnnotatedAnnotation</warning>(<warning descr="'nonAnnotatedAttributeInAnnotatedAnnotation' is declared in unstable @interface 'pkg.AnnotatedAnnotation' marked with @ApiStatus.Experimental">nonAnnotatedAttributeInAnnotatedAnnotation</warning> = "123") class C2
    @<warning descr="'pkg.AnnotatedAnnotation' is marked unstable with @ApiStatus.Experimental">AnnotatedAnnotation</warning>(<warning descr="'annotatedAttributeInAnnotatedAnnotation' is marked unstable with @ApiStatus.Experimental">annotatedAttributeInAnnotatedAnnotation</warning> = "123") class C3
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

class DirectOverrideNonAnnotatedMethodInAnnotatedClass : <warning descr="'AnnotatedClass()' is declared in unstable class 'pkg.AnnotatedClass' marked with @ApiStatus.Experimental"><warning descr="'pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning></warning>() {
  override fun <warning descr="Overridden method 'nonAnnotatedMethodInAnnotatedClass()' is declared in unstable class 'pkg.AnnotatedClass' marked with @ApiStatus.Experimental">nonAnnotatedMethodInAnnotatedClass</warning>() {}
}

class DirectOverrideAnnotatedMethodInAnnotatedClass : <warning descr="'AnnotatedClass()' is declared in unstable class 'pkg.AnnotatedClass' marked with @ApiStatus.Experimental"><warning descr="'pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning></warning>() {
  override fun <warning descr="Overridden method 'annotatedMethodInAnnotatedClass()' is marked unstable with @ApiStatus.Experimental">annotatedMethodInAnnotatedClass</warning>() {}
}

class WarningsOfExperimentalTypesInSignature {
  fun classUsage() {
    <warning descr="'pkg.ClassWithExperimentalTypeInSignature' is unstable because its signature references unstable class 'pkg.AnnotatedClass' marked with @ApiStatus.Experimental">ClassWithExperimentalTypeInSignature</warning><<warning descr="'pkg.AnnotatedClass' is marked unstable with @ApiStatus.Experimental">AnnotatedClass</warning>>()
  }

  fun membersUsages(owner: OwnerOfMembersWithExperimentalTypesInSignature) {
    val field = owner.<warning descr="'field' is unstable because its signature references unstable class 'pkg.AnnotatedClass' marked with @ApiStatus.Experimental">field</warning>
    owner.<warning descr="'parameterType(pkg.AnnotatedClass)' is unstable because its signature references unstable class 'pkg.AnnotatedClass' marked with @ApiStatus.Experimental">parameterType</warning>(null)
    owner.<warning descr="'returnType()' is unstable because its signature references unstable class 'pkg.AnnotatedClass' marked with @ApiStatus.Experimental">returnType</warning>()

    val fieldPkg = owner.<warning descr="'field' is unstable because its signature references unstable class 'pkg.AnnotatedClass' marked with @ApiStatus.Experimental">field</warning>
    owner.<warning descr="'parameterTypePkg(annotatedPkg.ClassInAnnotatedPkg)' is unstable because its signature references unstable class 'annotatedPkg.ClassInAnnotatedPkg' marked with @ApiStatus.Experimental">parameterTypePkg</warning>(null)
    owner.<warning descr="'returnTypePkg()' is unstable because its signature references unstable class 'pkg.AnnotatedClass' marked with @ApiStatus.Experimental">returnTypePkg</warning>()
  }
}