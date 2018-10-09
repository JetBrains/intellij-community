import pkg.AnnotatedClass
import pkg.AnnotatedClass.NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS
import pkg.AnnotatedClass.staticNonAnnotatedMethodInAnnotatedClass
import pkg.AnnotatedClass.ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS
import pkg.AnnotatedClass.staticAnnotatedMethodInAnnotatedClass

import pkg.NonAnnotatedClass 
import pkg.NonAnnotatedClass.NON_ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS 
import pkg.NonAnnotatedClass.staticNonAnnotatedMethodInNonAnnotatedClass 
import pkg.NonAnnotatedClass.ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS
import pkg.NonAnnotatedClass.staticAnnotatedMethodInNonAnnotatedClass

import pkg.AnnotatedEnum
import pkg.NonAnnotatedEnum 
import pkg.AnnotatedEnum.NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM
import pkg.AnnotatedEnum.ANNOTATED_VALUE_IN_ANNOTATED_ENUM
import pkg.NonAnnotatedEnum.NON_ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM 
import pkg.NonAnnotatedEnum.ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM

import pkg.AnnotatedAnnotation
import pkg.NonAnnotatedAnnotation

import annotatedPkg.ClassInAnnotatedPkg

@Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "UNUSED_VALUE")
class UnstableElementsIgnoreImportsTest {
  fun test() {
    var s = <warning descr="'AnnotatedClass' is marked unstable">AnnotatedClass</warning>.NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS 
    <warning descr="'AnnotatedClass' is marked unstable">AnnotatedClass</warning>.staticNonAnnotatedMethodInAnnotatedClass() 
    val annotatedClassInstanceViaNonAnnotatedConstructor : <warning descr="'AnnotatedClass' is marked unstable">AnnotatedClass</warning> = AnnotatedClass() 
    s = annotatedClassInstanceViaNonAnnotatedConstructor.nonAnnotatedFieldInAnnotatedClass 
    annotatedClassInstanceViaNonAnnotatedConstructor.nonAnnotatedMethodInAnnotatedClass() 
    s = NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS 
    staticNonAnnotatedMethodInAnnotatedClass() 

    s = <warning descr="'AnnotatedClass' is marked unstable">AnnotatedClass</warning>.<warning descr="'ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is marked unstable">ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</warning> 
    <warning descr="'AnnotatedClass' is marked unstable">AnnotatedClass</warning>.<warning descr="'staticAnnotatedMethodInAnnotatedClass' is marked unstable">staticAnnotatedMethodInAnnotatedClass</warning>() 
    val annotatedClassInstanceViaAnnotatedConstructor : <warning descr="'AnnotatedClass' is marked unstable">AnnotatedClass</warning> = <warning descr="'AnnotatedClass' is marked unstable">AnnotatedClass</warning>("") 
    s = annotatedClassInstanceViaAnnotatedConstructor.<warning descr="'annotatedFieldInAnnotatedClass' is marked unstable">annotatedFieldInAnnotatedClass</warning> 
    annotatedClassInstanceViaAnnotatedConstructor.<warning descr="'annotatedMethodInAnnotatedClass' is marked unstable">annotatedMethodInAnnotatedClass</warning>() 
    s = <warning descr="'ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is marked unstable">ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</warning> 
    <warning descr="'staticAnnotatedMethodInAnnotatedClass' is marked unstable">staticAnnotatedMethodInAnnotatedClass</warning>() 

    // ---------------------------------

    s = NonAnnotatedClass.NON_ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS 
    NonAnnotatedClass.staticNonAnnotatedMethodInNonAnnotatedClass() 
    val nonAnnotatedClassInstanceViaNonAnnotatedConstructor = NonAnnotatedClass() 
    s = nonAnnotatedClassInstanceViaNonAnnotatedConstructor.nonAnnotatedFieldInNonAnnotatedClass 
    nonAnnotatedClassInstanceViaNonAnnotatedConstructor.nonAnnotatedMethodInNonAnnotatedClass() 
    s = NON_ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS 
    staticNonAnnotatedMethodInNonAnnotatedClass() 

    s = NonAnnotatedClass.<warning descr="'ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS' is marked unstable">ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS</warning> 
    NonAnnotatedClass.<warning descr="'staticAnnotatedMethodInNonAnnotatedClass' is marked unstable">staticAnnotatedMethodInNonAnnotatedClass</warning>() 
    val nonAnnotatedClassInstanceViaAnnotatedConstructor = <warning descr="'NonAnnotatedClass' is marked unstable">NonAnnotatedClass</warning>("") 
    s = nonAnnotatedClassInstanceViaAnnotatedConstructor.<warning descr="'annotatedFieldInNonAnnotatedClass' is marked unstable">annotatedFieldInNonAnnotatedClass</warning> 
    nonAnnotatedClassInstanceViaAnnotatedConstructor.<warning descr="'annotatedMethodInNonAnnotatedClass' is marked unstable">annotatedMethodInNonAnnotatedClass</warning>() 
    s = <warning descr="'ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS' is marked unstable">ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS</warning> 
    <warning descr="'staticAnnotatedMethodInNonAnnotatedClass' is marked unstable">staticAnnotatedMethodInNonAnnotatedClass</warning>() 

    // ---------------------------------

    var nonAnnotatedValueInAnnotatedEnum : <warning descr="'AnnotatedEnum' is marked unstable">AnnotatedEnum</warning> = <warning descr="'AnnotatedEnum' is marked unstable">AnnotatedEnum</warning>.NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM 
    nonAnnotatedValueInAnnotatedEnum = NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM 
    var annotatedValueInAnnotatedEnum : <warning descr="'AnnotatedEnum' is marked unstable">AnnotatedEnum</warning> = <warning descr="'AnnotatedEnum' is marked unstable">AnnotatedEnum</warning>.<warning descr="'ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is marked unstable">ANNOTATED_VALUE_IN_ANNOTATED_ENUM</warning> 
    annotatedValueInAnnotatedEnum = <warning descr="'ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is marked unstable">ANNOTATED_VALUE_IN_ANNOTATED_ENUM</warning> 

    var nonAnnotatedValueInNonAnnotatedEnum = NonAnnotatedEnum.NON_ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM 
    nonAnnotatedValueInNonAnnotatedEnum = NON_ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM 
    var annotatedValueInNonAnnotatedEnum = NonAnnotatedEnum.<warning descr="'ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM' is marked unstable">ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM</warning> 
    annotatedValueInNonAnnotatedEnum = <warning descr="'ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM' is marked unstable">ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM</warning> 

    // ---------------------------------

    @<warning descr="'AnnotatedAnnotation' is marked unstable">AnnotatedAnnotation</warning> class C1 
    @<warning descr="'AnnotatedAnnotation' is marked unstable">AnnotatedAnnotation</warning>(nonAnnotatedAttributeInAnnotatedAnnotation = "123") class C2 
    @<warning descr="'AnnotatedAnnotation' is marked unstable">AnnotatedAnnotation</warning>(<warning descr="'annotatedAttributeInAnnotatedAnnotation' is marked unstable">annotatedAttributeInAnnotatedAnnotation</warning> = "123") class C3 
    @NonAnnotatedAnnotation class C4 
    @NonAnnotatedAnnotation(nonAnnotatedAttributeInNonAnnotatedAnnotation = "123") class C5 
    @NonAnnotatedAnnotation(<warning descr="'annotatedAttributeInNonAnnotatedAnnotation' is marked unstable">annotatedAttributeInNonAnnotatedAnnotation</warning> = "123") class C6 
  }
}