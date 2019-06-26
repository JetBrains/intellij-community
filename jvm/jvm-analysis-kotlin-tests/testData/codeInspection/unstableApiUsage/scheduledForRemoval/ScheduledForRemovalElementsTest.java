// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import pkg.<warning descr="'pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</warning>;
import pkg.<warning descr="'pkg.ClassWithScheduledForRemovalTypeInSignature' is scheduled for removal because its signature references class 'pkg.AnnotatedClass' scheduled for removal in version 123.456">ClassWithScheduledForRemovalTypeInSignature</warning>;
import pkg.OwnerOfMembersWithScheduledForRemovalTypesInSignature;
import static pkg.<warning descr="'pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</warning>.<warning descr="'NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is declared in class 'pkg.AnnotatedClass' scheduled for removal in version 123.456">NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</warning>;
import static pkg.<warning descr="'pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</warning>.<warning descr="'staticNonAnnotatedMethodInAnnotatedClass()' is declared in class 'pkg.AnnotatedClass' scheduled for removal in version 123.456">staticNonAnnotatedMethodInAnnotatedClass</warning>;
import static pkg.<warning descr="'pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</warning>.<warning descr="'ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is scheduled for removal in version 123.456">ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</warning>;
import static pkg.<warning descr="'pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</warning>.<warning descr="'staticAnnotatedMethodInAnnotatedClass()' is scheduled for removal in version 123.456">staticAnnotatedMethodInAnnotatedClass</warning>;

import pkg.NonAnnotatedClass;
import static pkg.NonAnnotatedClass.NON_ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS;
import static pkg.NonAnnotatedClass.staticNonAnnotatedMethodInNonAnnotatedClass;
import static pkg.NonAnnotatedClass.<warning descr="'ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS' is scheduled for removal in version 123.456">ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS</warning>;
import static pkg.NonAnnotatedClass.<warning descr="'staticAnnotatedMethodInNonAnnotatedClass()' is scheduled for removal in version 123.456">staticAnnotatedMethodInNonAnnotatedClass</warning>;

import pkg.<warning descr="'pkg.AnnotatedEnum' is scheduled for removal in version 123.456">AnnotatedEnum</warning>;
import pkg.NonAnnotatedEnum;
import static pkg.<warning descr="'pkg.AnnotatedEnum' is scheduled for removal in version 123.456">AnnotatedEnum</warning>.<warning descr="'NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is declared in enum 'pkg.AnnotatedEnum' scheduled for removal in version 123.456">NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM</warning>;
import static pkg.<warning descr="'pkg.AnnotatedEnum' is scheduled for removal in version 123.456">AnnotatedEnum</warning>.<warning descr="'ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is scheduled for removal in version 123.456">ANNOTATED_VALUE_IN_ANNOTATED_ENUM</warning>;
import static pkg.NonAnnotatedEnum.NON_ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM;
import static pkg.NonAnnotatedEnum.<warning descr="'ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM' is scheduled for removal in version 123.456">ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM</warning>;

import pkg.<warning descr="'pkg.AnnotatedAnnotation' is scheduled for removal in version 123.456">AnnotatedAnnotation</warning>;
import pkg.NonAnnotatedAnnotation;

import <warning descr="'annotatedPkg' is scheduled for removal in version 123.456">annotatedPkg</warning>.<warning descr="'annotatedPkg.ClassInAnnotatedPkg' is declared in package 'annotatedPkg' scheduled for removal in version 123.456">ClassInAnnotatedPkg</warning>;

public class ScheduledForRemovalElementsTest {
  public void test() {
    String s = <warning descr="'pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</warning>.<warning descr="'NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is declared in class 'pkg.AnnotatedClass' scheduled for removal in version 123.456">NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</warning>;
    <warning descr="'pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</warning>.<warning descr="'staticNonAnnotatedMethodInAnnotatedClass()' is declared in class 'pkg.AnnotatedClass' scheduled for removal in version 123.456">staticNonAnnotatedMethodInAnnotatedClass</warning>();
    <warning descr="'pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</warning> annotatedClassInstanceViaNonAnnotatedConstructor = new <warning descr="'AnnotatedClass()' is declared in class 'pkg.AnnotatedClass' scheduled for removal in version 123.456"><warning descr="'pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</warning></warning>();
    s = annotatedClassInstanceViaNonAnnotatedConstructor.<warning descr="'nonAnnotatedFieldInAnnotatedClass' is declared in class 'pkg.AnnotatedClass' scheduled for removal in version 123.456">nonAnnotatedFieldInAnnotatedClass</warning>;
    annotatedClassInstanceViaNonAnnotatedConstructor.<warning descr="'nonAnnotatedMethodInAnnotatedClass()' is declared in class 'pkg.AnnotatedClass' scheduled for removal in version 123.456">nonAnnotatedMethodInAnnotatedClass</warning>();
    s = <warning descr="'NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is declared in class 'pkg.AnnotatedClass' scheduled for removal in version 123.456">NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</warning>;
    <warning descr="'staticNonAnnotatedMethodInAnnotatedClass()' is declared in class 'pkg.AnnotatedClass' scheduled for removal in version 123.456">staticNonAnnotatedMethodInAnnotatedClass</warning>();

    s = <warning descr="'pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</warning>.<warning descr="'ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is scheduled for removal in version 123.456">ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</warning>;
    <warning descr="'pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</warning>.<warning descr="'staticAnnotatedMethodInAnnotatedClass()' is scheduled for removal in version 123.456">staticAnnotatedMethodInAnnotatedClass</warning>();
    <warning descr="'pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</warning> annotatedClassInstanceViaAnnotatedConstructor = new <warning descr="'AnnotatedClass(java.lang.String)' is scheduled for removal in version 123.456"><warning descr="'pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</warning></warning>("");
    s = annotatedClassInstanceViaAnnotatedConstructor.<warning descr="'annotatedFieldInAnnotatedClass' is scheduled for removal in version 123.456">annotatedFieldInAnnotatedClass</warning>;
    annotatedClassInstanceViaAnnotatedConstructor.<warning descr="'annotatedMethodInAnnotatedClass()' is scheduled for removal in version 123.456">annotatedMethodInAnnotatedClass</warning>();
    s = <warning descr="'ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is scheduled for removal in version 123.456">ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</warning>;
    <warning descr="'staticAnnotatedMethodInAnnotatedClass()' is scheduled for removal in version 123.456">staticAnnotatedMethodInAnnotatedClass</warning>();

    // ---------------------------------

    s = NonAnnotatedClass.NON_ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS;
    NonAnnotatedClass.staticNonAnnotatedMethodInNonAnnotatedClass();
    NonAnnotatedClass nonAnnotatedClassInstanceViaNonAnnotatedConstructor = new NonAnnotatedClass();
    s = nonAnnotatedClassInstanceViaNonAnnotatedConstructor.nonAnnotatedFieldInNonAnnotatedClass;
    nonAnnotatedClassInstanceViaNonAnnotatedConstructor.nonAnnotatedMethodInNonAnnotatedClass();
    s = NON_ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS;
    staticNonAnnotatedMethodInNonAnnotatedClass();

    s = NonAnnotatedClass.<warning descr="'ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS' is scheduled for removal in version 123.456">ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS</warning>;
    NonAnnotatedClass.<warning descr="'staticAnnotatedMethodInNonAnnotatedClass()' is scheduled for removal in version 123.456">staticAnnotatedMethodInNonAnnotatedClass</warning>();
    NonAnnotatedClass nonAnnotatedClassInstanceViaAnnotatedConstructor = new <warning descr="'NonAnnotatedClass(java.lang.String)' is scheduled for removal in version 123.456">NonAnnotatedClass</warning>("");
    s = nonAnnotatedClassInstanceViaAnnotatedConstructor.<warning descr="'annotatedFieldInNonAnnotatedClass' is scheduled for removal in version 123.456">annotatedFieldInNonAnnotatedClass</warning>;
    nonAnnotatedClassInstanceViaAnnotatedConstructor.<warning descr="'annotatedMethodInNonAnnotatedClass()' is scheduled for removal in version 123.456">annotatedMethodInNonAnnotatedClass</warning>();
    s = <warning descr="'ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS' is scheduled for removal in version 123.456">ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS</warning>;
    <warning descr="'staticAnnotatedMethodInNonAnnotatedClass()' is scheduled for removal in version 123.456">staticAnnotatedMethodInNonAnnotatedClass</warning>();

    // ---------------------------------

    <warning descr="'pkg.AnnotatedEnum' is scheduled for removal in version 123.456">AnnotatedEnum</warning> nonAnnotatedValueInAnnotatedEnum = <warning descr="'pkg.AnnotatedEnum' is scheduled for removal in version 123.456">AnnotatedEnum</warning>.<warning descr="'NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is declared in enum 'pkg.AnnotatedEnum' scheduled for removal in version 123.456">NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM</warning>;
    nonAnnotatedValueInAnnotatedEnum = <warning descr="'NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is declared in enum 'pkg.AnnotatedEnum' scheduled for removal in version 123.456">NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM</warning>;
    <warning descr="'pkg.AnnotatedEnum' is scheduled for removal in version 123.456">AnnotatedEnum</warning> annotatedValueInAnnotatedEnum = <warning descr="'pkg.AnnotatedEnum' is scheduled for removal in version 123.456">AnnotatedEnum</warning>.<warning descr="'ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is scheduled for removal in version 123.456">ANNOTATED_VALUE_IN_ANNOTATED_ENUM</warning>;
    annotatedValueInAnnotatedEnum = <warning descr="'ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is scheduled for removal in version 123.456">ANNOTATED_VALUE_IN_ANNOTATED_ENUM</warning>;

    NonAnnotatedEnum nonAnnotatedValueInNonAnnotatedEnum = NonAnnotatedEnum.NON_ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM;
    nonAnnotatedValueInNonAnnotatedEnum = NON_ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM;
    NonAnnotatedEnum annotatedValueInNonAnnotatedEnum = NonAnnotatedEnum.<warning descr="'ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM' is scheduled for removal in version 123.456">ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM</warning>;
    annotatedValueInNonAnnotatedEnum = <warning descr="'ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM' is scheduled for removal in version 123.456">ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM</warning>;
    
    // ---------------------------------

    @<warning descr="'pkg.AnnotatedAnnotation' is scheduled for removal in version 123.456">AnnotatedAnnotation</warning> class C1 {}
    @<warning descr="'pkg.AnnotatedAnnotation' is scheduled for removal in version 123.456">AnnotatedAnnotation</warning>(<warning descr="'nonAnnotatedAttributeInAnnotatedAnnotation' is declared in @interface 'pkg.AnnotatedAnnotation' scheduled for removal in version 123.456">nonAnnotatedAttributeInAnnotatedAnnotation</warning> = "123") class C2 {}
    @<warning descr="'pkg.AnnotatedAnnotation' is scheduled for removal in version 123.456">AnnotatedAnnotation</warning>(<warning descr="'annotatedAttributeInAnnotatedAnnotation' is scheduled for removal in version 123.456">annotatedAttributeInAnnotatedAnnotation</warning> = "123") class C3 {}
    @NonAnnotatedAnnotation class C4 {}
    @NonAnnotatedAnnotation(nonAnnotatedAttributeInNonAnnotatedAnnotation = "123") class C5 {}
    @NonAnnotatedAnnotation(<warning descr="'annotatedAttributeInNonAnnotatedAnnotation' is scheduled for removal in version 123.456">annotatedAttributeInNonAnnotatedAnnotation</warning> = "123") class C6 {}
  }
}

class DirectOverrideAnnotatedMethod extends NonAnnotatedClass {
  @Override
  public void <warning descr="Overridden method 'annotatedMethodInNonAnnotatedClass()' is scheduled for removal in version 123.456">annotatedMethodInNonAnnotatedClass</warning>() {}
}

class IndirectOverrideAnnotatedMethod extends DirectOverrideAnnotatedMethod {
  @Override
  public void annotatedMethodInNonAnnotatedClass() {}
}

class WarningsOfScheduledForRemovalTypesInSignature {
  public void classUsage() {
    new <warning descr="'pkg.ClassWithScheduledForRemovalTypeInSignature' is scheduled for removal because its signature references class 'pkg.AnnotatedClass' scheduled for removal in version 123.456">ClassWithScheduledForRemovalTypeInSignature<<warning descr="'pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</warning>></warning>();
  }

  public void membersUsages(OwnerOfMembersWithScheduledForRemovalTypesInSignature owner) {
    Object field = owner.<warning descr="'field' is scheduled for removal because its signature references class 'pkg.AnnotatedClass' scheduled for removal in version 123.456">field</warning>;
    owner.<warning descr="'parameterType(pkg.AnnotatedClass)' is scheduled for removal because its signature references class 'pkg.AnnotatedClass' scheduled for removal in version 123.456">parameterType</warning>(null);
    owner.<warning descr="'returnType()' is scheduled for removal because its signature references class 'pkg.AnnotatedClass' scheduled for removal in version 123.456">returnType</warning>();

    Object fieldPkg = owner.<warning descr="'field' is scheduled for removal because its signature references class 'pkg.AnnotatedClass' scheduled for removal in version 123.456">field</warning>;
    owner.<warning descr="'parameterTypePkg(annotatedPkg.ClassInAnnotatedPkg)' is scheduled for removal because its signature references class 'annotatedPkg.ClassInAnnotatedPkg' scheduled for removal in version 123.456">parameterTypePkg</warning>(null);
    owner.<warning descr="'returnTypePkg()' is scheduled for removal because its signature references class 'pkg.AnnotatedClass' scheduled for removal in version 123.456">returnTypePkg</warning>();
  }
}