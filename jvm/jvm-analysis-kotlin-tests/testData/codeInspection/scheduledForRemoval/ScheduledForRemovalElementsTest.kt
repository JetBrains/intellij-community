import pkg.<warning descr="'pkg.AnnotatedClass' is scheduled for removal in 123.456">AnnotatedClass</warning>
import pkg.<warning descr="'pkg.AnnotatedClass' is scheduled for removal in 123.456">AnnotatedClass</warning>.NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS
import pkg.<warning descr="'pkg.AnnotatedClass' is scheduled for removal in 123.456">AnnotatedClass</warning>.staticNonAnnotatedMethodInAnnotatedClass
import pkg.<warning descr="'pkg.AnnotatedClass' is scheduled for removal in 123.456">AnnotatedClass</warning>.<warning descr="'ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is scheduled for removal in 123.456">ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</warning>
import pkg.<warning descr="'pkg.AnnotatedClass' is scheduled for removal in 123.456">AnnotatedClass</warning>.<warning descr="'staticAnnotatedMethodInAnnotatedClass()' is scheduled for removal in 123.456">staticAnnotatedMethodInAnnotatedClass</warning>

import pkg.NonAnnotatedClass 
import pkg.NonAnnotatedClass.NON_ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS 
import pkg.NonAnnotatedClass.staticNonAnnotatedMethodInNonAnnotatedClass 
import pkg.NonAnnotatedClass.<warning descr="'ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS' is scheduled for removal in 123.456">ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS</warning>
import pkg.NonAnnotatedClass.<warning descr="'staticAnnotatedMethodInNonAnnotatedClass()' is scheduled for removal in 123.456">staticAnnotatedMethodInNonAnnotatedClass</warning>

import pkg.<warning descr="'pkg.AnnotatedEnum' is scheduled for removal in 123.456">AnnotatedEnum</warning>
import pkg.NonAnnotatedEnum 
import pkg.<warning descr="'pkg.AnnotatedEnum' is scheduled for removal in 123.456">AnnotatedEnum</warning>.NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM
import pkg.<warning descr="'pkg.AnnotatedEnum' is scheduled for removal in 123.456">AnnotatedEnum</warning>.<warning descr="'ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is scheduled for removal in 123.456">ANNOTATED_VALUE_IN_ANNOTATED_ENUM</warning>
import pkg.NonAnnotatedEnum.NON_ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM 
import pkg.NonAnnotatedEnum.<warning descr="'ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM' is scheduled for removal in 123.456">ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM</warning>

import pkg.<warning descr="'pkg.AnnotatedAnnotation' is scheduled for removal in 123.456">AnnotatedAnnotation</warning>
import pkg.NonAnnotatedAnnotation

import <warning descr="'annotatedPkg' is scheduled for removal in 123.456">annotatedPkg</warning>.ClassInAnnotatedPkg

@Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "UNUSED_VALUE")
class ScheduledForRemovalElementsTest {
  fun test() {
    var s = <warning descr="'pkg.AnnotatedClass' is scheduled for removal in 123.456">AnnotatedClass</warning>.NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS
    <warning descr="'pkg.AnnotatedClass' is scheduled for removal in 123.456">AnnotatedClass</warning>.staticNonAnnotatedMethodInAnnotatedClass()
    val annotatedClassInstanceViaNonAnnotatedConstructor : <warning descr="'pkg.AnnotatedClass' is scheduled for removal in 123.456">AnnotatedClass</warning> = AnnotatedClass()
    s = annotatedClassInstanceViaNonAnnotatedConstructor.nonAnnotatedFieldInAnnotatedClass 
    annotatedClassInstanceViaNonAnnotatedConstructor.nonAnnotatedMethodInAnnotatedClass() 
    s = NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS 
    staticNonAnnotatedMethodInAnnotatedClass() 

    s = <warning descr="'pkg.AnnotatedClass' is scheduled for removal in 123.456">AnnotatedClass</warning>.<warning descr="'ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is scheduled for removal in 123.456">ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</warning>
    <warning descr="'pkg.AnnotatedClass' is scheduled for removal in 123.456">AnnotatedClass</warning>.<warning descr="'staticAnnotatedMethodInAnnotatedClass()' is scheduled for removal in 123.456">staticAnnotatedMethodInAnnotatedClass</warning>()
    val annotatedClassInstanceViaAnnotatedConstructor : <warning descr="'pkg.AnnotatedClass' is scheduled for removal in 123.456">AnnotatedClass</warning> = <warning descr="'AnnotatedClass(java.lang.String)' is scheduled for removal in 123.456">AnnotatedClass</warning>("")
    s = annotatedClassInstanceViaAnnotatedConstructor.<warning descr="'annotatedFieldInAnnotatedClass' is scheduled for removal in 123.456">annotatedFieldInAnnotatedClass</warning>
    annotatedClassInstanceViaAnnotatedConstructor.<warning descr="'annotatedMethodInAnnotatedClass()' is scheduled for removal in 123.456">annotatedMethodInAnnotatedClass</warning>()
    s = <warning descr="'ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is scheduled for removal in 123.456">ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</warning>
    <warning descr="'staticAnnotatedMethodInAnnotatedClass()' is scheduled for removal in 123.456">staticAnnotatedMethodInAnnotatedClass</warning>()

    // ---------------------------------

    s = NonAnnotatedClass.NON_ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS 
    NonAnnotatedClass.staticNonAnnotatedMethodInNonAnnotatedClass() 
    val nonAnnotatedClassInstanceViaNonAnnotatedConstructor = NonAnnotatedClass() 
    s = nonAnnotatedClassInstanceViaNonAnnotatedConstructor.nonAnnotatedFieldInNonAnnotatedClass 
    nonAnnotatedClassInstanceViaNonAnnotatedConstructor.nonAnnotatedMethodInNonAnnotatedClass() 
    s = NON_ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS 
    staticNonAnnotatedMethodInNonAnnotatedClass() 

    s = NonAnnotatedClass.<warning descr="'ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS' is scheduled for removal in 123.456">ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS</warning>
    NonAnnotatedClass.<warning descr="'staticAnnotatedMethodInNonAnnotatedClass()' is scheduled for removal in 123.456">staticAnnotatedMethodInNonAnnotatedClass</warning>()
    val nonAnnotatedClassInstanceViaAnnotatedConstructor = <warning descr="'NonAnnotatedClass(java.lang.String)' is scheduled for removal in 123.456">NonAnnotatedClass</warning>("")
    s = nonAnnotatedClassInstanceViaAnnotatedConstructor.<warning descr="'annotatedFieldInNonAnnotatedClass' is scheduled for removal in 123.456">annotatedFieldInNonAnnotatedClass</warning>
    nonAnnotatedClassInstanceViaAnnotatedConstructor.<warning descr="'annotatedMethodInNonAnnotatedClass()' is scheduled for removal in 123.456">annotatedMethodInNonAnnotatedClass</warning>()
    s = <warning descr="'ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS' is scheduled for removal in 123.456">ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS</warning>
    <warning descr="'staticAnnotatedMethodInNonAnnotatedClass()' is scheduled for removal in 123.456">staticAnnotatedMethodInNonAnnotatedClass</warning>()

    // ---------------------------------

    var nonAnnotatedValueInAnnotatedEnum : <warning descr="'pkg.AnnotatedEnum' is scheduled for removal in 123.456">AnnotatedEnum</warning> = <warning descr="'pkg.AnnotatedEnum' is scheduled for removal in 123.456">AnnotatedEnum</warning>.NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM
    nonAnnotatedValueInAnnotatedEnum = NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM 
    var annotatedValueInAnnotatedEnum : <warning descr="'pkg.AnnotatedEnum' is scheduled for removal in 123.456">AnnotatedEnum</warning> = <warning descr="'pkg.AnnotatedEnum' is scheduled for removal in 123.456">AnnotatedEnum</warning>.<warning descr="'ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is scheduled for removal in 123.456">ANNOTATED_VALUE_IN_ANNOTATED_ENUM</warning>
    annotatedValueInAnnotatedEnum = <warning descr="'ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is scheduled for removal in 123.456">ANNOTATED_VALUE_IN_ANNOTATED_ENUM</warning>

    var nonAnnotatedValueInNonAnnotatedEnum = NonAnnotatedEnum.NON_ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM 
    nonAnnotatedValueInNonAnnotatedEnum = NON_ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM 
    var annotatedValueInNonAnnotatedEnum = NonAnnotatedEnum.<warning descr="'ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM' is scheduled for removal in 123.456">ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM</warning>
    annotatedValueInNonAnnotatedEnum = <warning descr="'ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM' is scheduled for removal in 123.456">ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM</warning>

    // ---------------------------------

    @<warning descr="'pkg.AnnotatedAnnotation' is scheduled for removal in 123.456">AnnotatedAnnotation</warning> class C1
    @<warning descr="'pkg.AnnotatedAnnotation' is scheduled for removal in 123.456">AnnotatedAnnotation</warning>(nonAnnotatedAttributeInAnnotatedAnnotation = "123") class C2
    @<warning descr="'pkg.AnnotatedAnnotation' is scheduled for removal in 123.456">AnnotatedAnnotation</warning>(<warning descr="'annotatedAttributeInAnnotatedAnnotation' is scheduled for removal in 123.456">annotatedAttributeInAnnotatedAnnotation</warning> = "123") class C3
    @NonAnnotatedAnnotation class C4 
    @NonAnnotatedAnnotation(nonAnnotatedAttributeInNonAnnotatedAnnotation = "123") class C5 
    @NonAnnotatedAnnotation(<warning descr="'annotatedAttributeInNonAnnotatedAnnotation' is scheduled for removal in 123.456">annotatedAttributeInNonAnnotatedAnnotation</warning> = "123") class C6
  }
}