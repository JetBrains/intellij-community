@file:Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "UNUSED_VALUE", "UNUSED_PARAMETER", "UNUSED_VARIABLE")

import pkg.<warning descr="'pkg.AnnotatedClass' is marked unstable">AnnotatedClass</warning>
import pkg.<warning descr="'pkg.ClassWithExperimentalTypeInSignature' is unstable because its signature references unstable class 'pkg.AnnotatedClass'">ClassWithExperimentalTypeInSignature</warning>
import pkg.OwnerOfMembersWithExperimentalTypesInSignature
import pkg.<warning descr="'pkg.AnnotatedClass' is marked unstable">AnnotatedClass</warning>.<warning descr="'NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is declared in unstable class 'pkg.AnnotatedClass'">NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</warning>
import pkg.<warning descr="'pkg.AnnotatedClass' is marked unstable">AnnotatedClass</warning>.<warning descr="'staticNonAnnotatedMethodInAnnotatedClass()' is declared in unstable class 'pkg.AnnotatedClass'">staticNonAnnotatedMethodInAnnotatedClass</warning>
import pkg.<warning descr="'pkg.AnnotatedClass' is marked unstable">AnnotatedClass</warning>.<warning descr="'ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is marked unstable">ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</warning>
import pkg.<warning descr="'pkg.AnnotatedClass' is marked unstable">AnnotatedClass</warning>.<warning descr="'staticAnnotatedMethodInAnnotatedClass()' is marked unstable">staticAnnotatedMethodInAnnotatedClass</warning>

import pkg.NonAnnotatedClass 
import pkg.NonAnnotatedClass.NON_ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS 
import pkg.NonAnnotatedClass.staticNonAnnotatedMethodInNonAnnotatedClass 
import pkg.NonAnnotatedClass.<warning descr="'ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS' is marked unstable">ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS</warning> 
import pkg.NonAnnotatedClass.<warning descr="'staticAnnotatedMethodInNonAnnotatedClass()' is marked unstable">staticAnnotatedMethodInNonAnnotatedClass</warning>

import pkg.<warning descr="'pkg.AnnotatedEnum' is marked unstable">AnnotatedEnum</warning>
import pkg.NonAnnotatedEnum 
import pkg.<warning descr="'pkg.AnnotatedEnum' is marked unstable">AnnotatedEnum</warning>.<warning descr="'NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is declared in unstable enum 'pkg.AnnotatedEnum'">NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM</warning>
import pkg.<warning descr="'pkg.AnnotatedEnum' is marked unstable">AnnotatedEnum</warning>.<warning descr="'ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is marked unstable">ANNOTATED_VALUE_IN_ANNOTATED_ENUM</warning>
import pkg.NonAnnotatedEnum.NON_ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM 
import pkg.NonAnnotatedEnum.<warning descr="'ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM' is marked unstable">ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM</warning> 

import pkg.<warning descr="'pkg.AnnotatedAnnotation' is marked unstable">AnnotatedAnnotation</warning>
import pkg.NonAnnotatedAnnotation

import <warning descr="'annotatedPkg' is marked unstable">annotatedPkg</warning>.<warning descr="'annotatedPkg.ClassInAnnotatedPkg' is declared in unstable package 'annotatedPkg'">ClassInAnnotatedPkg</warning>

class UnstableElementsTest {
  fun test() {
    var s = <warning descr="'pkg.AnnotatedClass' is marked unstable">AnnotatedClass</warning>.<warning descr="'NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is declared in unstable class 'pkg.AnnotatedClass'">NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</warning>
    <warning descr="'pkg.AnnotatedClass' is marked unstable">AnnotatedClass</warning>.<warning descr="'staticNonAnnotatedMethodInAnnotatedClass()' is declared in unstable class 'pkg.AnnotatedClass'">staticNonAnnotatedMethodInAnnotatedClass</warning>()
    val annotatedClassInstanceViaNonAnnotatedConstructor : <warning descr="'pkg.AnnotatedClass' is marked unstable">AnnotatedClass</warning> = <warning descr="'AnnotatedClass()' is declared in unstable class 'pkg.AnnotatedClass'"><warning descr="'pkg.AnnotatedClass' is marked unstable">AnnotatedClass</warning></warning>()
    s = annotatedClassInstanceViaNonAnnotatedConstructor.<warning descr="'nonAnnotatedFieldInAnnotatedClass' is declared in unstable class 'pkg.AnnotatedClass'">nonAnnotatedFieldInAnnotatedClass</warning>
    annotatedClassInstanceViaNonAnnotatedConstructor.<warning descr="'nonAnnotatedMethodInAnnotatedClass()' is declared in unstable class 'pkg.AnnotatedClass'">nonAnnotatedMethodInAnnotatedClass</warning>()
    s = <warning descr="'NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is declared in unstable class 'pkg.AnnotatedClass'">NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</warning>
    <warning descr="'staticNonAnnotatedMethodInAnnotatedClass()' is declared in unstable class 'pkg.AnnotatedClass'">staticNonAnnotatedMethodInAnnotatedClass</warning>()

    s = <warning descr="'pkg.AnnotatedClass' is marked unstable">AnnotatedClass</warning>.<warning descr="'ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is marked unstable">ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</warning>
    <warning descr="'pkg.AnnotatedClass' is marked unstable">AnnotatedClass</warning>.<warning descr="'staticAnnotatedMethodInAnnotatedClass()' is marked unstable">staticAnnotatedMethodInAnnotatedClass</warning>()
    val annotatedClassInstanceViaAnnotatedConstructor : <warning descr="'pkg.AnnotatedClass' is marked unstable">AnnotatedClass</warning> = <warning descr="'AnnotatedClass(java.lang.String)' is marked unstable"><warning descr="'pkg.AnnotatedClass' is marked unstable">AnnotatedClass</warning></warning>("")
    s = annotatedClassInstanceViaAnnotatedConstructor.<warning descr="'annotatedFieldInAnnotatedClass' is marked unstable">annotatedFieldInAnnotatedClass</warning> 
    annotatedClassInstanceViaAnnotatedConstructor.<warning descr="'annotatedMethodInAnnotatedClass()' is marked unstable">annotatedMethodInAnnotatedClass</warning>()
    s = <warning descr="'ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is marked unstable">ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</warning> 
    <warning descr="'staticAnnotatedMethodInAnnotatedClass()' is marked unstable">staticAnnotatedMethodInAnnotatedClass</warning>()

    // ---------------------------------

    s = NonAnnotatedClass.NON_ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS 
    NonAnnotatedClass.staticNonAnnotatedMethodInNonAnnotatedClass() 
    val nonAnnotatedClassInstanceViaNonAnnotatedConstructor = NonAnnotatedClass() 
    s = nonAnnotatedClassInstanceViaNonAnnotatedConstructor.nonAnnotatedFieldInNonAnnotatedClass 
    nonAnnotatedClassInstanceViaNonAnnotatedConstructor.nonAnnotatedMethodInNonAnnotatedClass() 
    s = NON_ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS 
    staticNonAnnotatedMethodInNonAnnotatedClass() 

    s = NonAnnotatedClass.<warning descr="'ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS' is marked unstable">ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS</warning> 
    NonAnnotatedClass.<warning descr="'staticAnnotatedMethodInNonAnnotatedClass()' is marked unstable">staticAnnotatedMethodInNonAnnotatedClass</warning>()
    val nonAnnotatedClassInstanceViaAnnotatedConstructor = <warning descr="'NonAnnotatedClass(java.lang.String)' is marked unstable">NonAnnotatedClass</warning>("")
    s = nonAnnotatedClassInstanceViaAnnotatedConstructor.<warning descr="'annotatedFieldInNonAnnotatedClass' is marked unstable">annotatedFieldInNonAnnotatedClass</warning> 
    nonAnnotatedClassInstanceViaAnnotatedConstructor.<warning descr="'annotatedMethodInNonAnnotatedClass()' is marked unstable">annotatedMethodInNonAnnotatedClass</warning>()
    s = <warning descr="'ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS' is marked unstable">ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS</warning> 
    <warning descr="'staticAnnotatedMethodInNonAnnotatedClass()' is marked unstable">staticAnnotatedMethodInNonAnnotatedClass</warning>()

    // ---------------------------------

    var nonAnnotatedValueInAnnotatedEnum : <warning descr="'pkg.AnnotatedEnum' is marked unstable">AnnotatedEnum</warning> = <warning descr="'pkg.AnnotatedEnum' is marked unstable">AnnotatedEnum</warning>.<warning descr="'NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is declared in unstable enum 'pkg.AnnotatedEnum'">NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM</warning>
    nonAnnotatedValueInAnnotatedEnum = <warning descr="'NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is declared in unstable enum 'pkg.AnnotatedEnum'">NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM</warning>
    var annotatedValueInAnnotatedEnum : <warning descr="'pkg.AnnotatedEnum' is marked unstable">AnnotatedEnum</warning> = <warning descr="'pkg.AnnotatedEnum' is marked unstable">AnnotatedEnum</warning>.<warning descr="'ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is marked unstable">ANNOTATED_VALUE_IN_ANNOTATED_ENUM</warning>
    annotatedValueInAnnotatedEnum = <warning descr="'ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is marked unstable">ANNOTATED_VALUE_IN_ANNOTATED_ENUM</warning> 

    var nonAnnotatedValueInNonAnnotatedEnum = NonAnnotatedEnum.NON_ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM 
    nonAnnotatedValueInNonAnnotatedEnum = NON_ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM 
    var annotatedValueInNonAnnotatedEnum = NonAnnotatedEnum.<warning descr="'ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM' is marked unstable">ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM</warning> 
    annotatedValueInNonAnnotatedEnum = <warning descr="'ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM' is marked unstable">ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM</warning> 

    // ---------------------------------

    @<warning descr="'pkg.AnnotatedAnnotation' is marked unstable">AnnotatedAnnotation</warning> class C1
    @<warning descr="'pkg.AnnotatedAnnotation' is marked unstable">AnnotatedAnnotation</warning>(<warning descr="'nonAnnotatedAttributeInAnnotatedAnnotation' is declared in unstable @interface 'pkg.AnnotatedAnnotation'">nonAnnotatedAttributeInAnnotatedAnnotation</warning> = "123") class C2
    @<warning descr="'pkg.AnnotatedAnnotation' is marked unstable">AnnotatedAnnotation</warning>(<warning descr="'annotatedAttributeInAnnotatedAnnotation' is marked unstable">annotatedAttributeInAnnotatedAnnotation</warning> = "123") class C3
    @NonAnnotatedAnnotation class C4 
    @NonAnnotatedAnnotation(nonAnnotatedAttributeInNonAnnotatedAnnotation = "123") class C5 
    @NonAnnotatedAnnotation(<warning descr="'annotatedAttributeInNonAnnotatedAnnotation' is marked unstable">annotatedAttributeInNonAnnotatedAnnotation</warning> = "123") class C6 
  }
}

open class DirectOverrideAnnotatedMethod : NonAnnotatedClass() {
  override fun <warning descr="Overridden method 'annotatedMethodInNonAnnotatedClass()' is marked unstable">annotatedMethodInNonAnnotatedClass</warning>() {}
}

//No warning should be produced.
class IndirectOverrideAnnotatedMethod : DirectOverrideAnnotatedMethod() {
  override fun annotatedMethodInNonAnnotatedClass() {}
}

class DirectOverrideNonAnnotatedMethodInAnnotatedClass : <warning descr="'AnnotatedClass()' is declared in unstable class 'pkg.AnnotatedClass'"><warning descr="'pkg.AnnotatedClass' is marked unstable">AnnotatedClass</warning></warning>() {
  override fun <warning descr="Overridden method 'nonAnnotatedMethodInAnnotatedClass()' is declared in unstable class 'pkg.AnnotatedClass'">nonAnnotatedMethodInAnnotatedClass</warning>() {}
}

class DirectOverrideAnnotatedMethodInAnnotatedClass : <warning descr="'AnnotatedClass()' is declared in unstable class 'pkg.AnnotatedClass'"><warning descr="'pkg.AnnotatedClass' is marked unstable">AnnotatedClass</warning></warning>() {
  override fun <warning descr="Overridden method 'annotatedMethodInAnnotatedClass()' is marked unstable">annotatedMethodInAnnotatedClass</warning>() {}
}

class WarningsOfExperimentalTypesInSignature {
  fun classUsage() {
    <warning descr="'pkg.ClassWithExperimentalTypeInSignature' is unstable because its signature references unstable class 'pkg.AnnotatedClass'">ClassWithExperimentalTypeInSignature</warning><<warning descr="'pkg.AnnotatedClass' is marked unstable">AnnotatedClass</warning>>()
  }

  fun membersUsages(owner: OwnerOfMembersWithExperimentalTypesInSignature) {
    val field = owner.<warning descr="'field' is unstable because its signature references unstable class 'pkg.AnnotatedClass'">field</warning>
    owner.<warning descr="'parameterType(pkg.AnnotatedClass)' is unstable because its signature references unstable class 'pkg.AnnotatedClass'">parameterType</warning>(null)
    owner.<warning descr="'returnType()' is unstable because its signature references unstable class 'pkg.AnnotatedClass'">returnType</warning>()

    val fieldPkg = owner.<warning descr="'field' is unstable because its signature references unstable class 'pkg.AnnotatedClass'">field</warning>
    owner.<warning descr="'parameterTypePkg(annotatedPkg.ClassInAnnotatedPkg)' is unstable because its signature references unstable class 'annotatedPkg.ClassInAnnotatedPkg'">parameterTypePkg</warning>(null)
    owner.<warning descr="'returnTypePkg()' is unstable because its signature references unstable class 'pkg.AnnotatedClass'">returnTypePkg</warning>()
  }
}