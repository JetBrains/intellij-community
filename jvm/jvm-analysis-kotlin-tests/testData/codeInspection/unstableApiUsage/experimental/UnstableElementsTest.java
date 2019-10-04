// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import pkg.<warning descr="'pkg.AnnotatedClass' is marked unstable">AnnotatedClass</warning>;
import pkg.<warning descr="'pkg.ClassWithExperimentalTypeInSignature' is unstable because its signature references unstable class 'pkg.AnnotatedClass'">ClassWithExperimentalTypeInSignature</warning>;
import pkg.OwnerOfMembersWithExperimentalTypesInSignature;
import static pkg.<warning descr="'pkg.AnnotatedClass' is marked unstable">AnnotatedClass</warning>.<warning descr="'NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is declared in unstable class 'pkg.AnnotatedClass'">NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</warning>;
import static pkg.<warning descr="'pkg.AnnotatedClass' is marked unstable">AnnotatedClass</warning>.<warning descr="'staticNonAnnotatedMethodInAnnotatedClass()' is declared in unstable class 'pkg.AnnotatedClass'">staticNonAnnotatedMethodInAnnotatedClass</warning>;
import static pkg.<warning descr="'pkg.AnnotatedClass' is marked unstable">AnnotatedClass</warning>.<warning descr="'ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is marked unstable">ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</warning>;
import static pkg.<warning descr="'pkg.AnnotatedClass' is marked unstable">AnnotatedClass</warning>.<warning descr="'staticAnnotatedMethodInAnnotatedClass()' is marked unstable">staticAnnotatedMethodInAnnotatedClass</warning>;

import pkg.NonAnnotatedClass;
import static pkg.NonAnnotatedClass.NON_ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS;
import static pkg.NonAnnotatedClass.staticNonAnnotatedMethodInNonAnnotatedClass;
import static pkg.NonAnnotatedClass.<warning descr="'ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS' is marked unstable">ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS</warning>;
import static pkg.NonAnnotatedClass.<warning descr="'staticAnnotatedMethodInNonAnnotatedClass()' is marked unstable">staticAnnotatedMethodInNonAnnotatedClass</warning>;

import pkg.<warning descr="'pkg.AnnotatedEnum' is marked unstable">AnnotatedEnum</warning>;
import pkg.NonAnnotatedEnum;
import static pkg.<warning descr="'pkg.AnnotatedEnum' is marked unstable">AnnotatedEnum</warning>.<warning descr="'NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is declared in unstable enum 'pkg.AnnotatedEnum'">NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM</warning>;
import static pkg.<warning descr="'pkg.AnnotatedEnum' is marked unstable">AnnotatedEnum</warning>.<warning descr="'ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is marked unstable">ANNOTATED_VALUE_IN_ANNOTATED_ENUM</warning>;
import static pkg.NonAnnotatedEnum.NON_ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM;
import static pkg.NonAnnotatedEnum.<warning descr="'ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM' is marked unstable">ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM</warning>;

import pkg.<warning descr="'pkg.AnnotatedAnnotation' is marked unstable">AnnotatedAnnotation</warning>;
import pkg.NonAnnotatedAnnotation;

import <warning descr="'annotatedPkg' is marked unstable">annotatedPkg</warning>.<warning descr="'annotatedPkg.ClassInAnnotatedPkg' is declared in unstable package 'annotatedPkg'">ClassInAnnotatedPkg</warning>;

public class UnstableElementsTest {
  public void test() {
    String s = <warning descr="'pkg.AnnotatedClass' is marked unstable">AnnotatedClass</warning>.<warning descr="'NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is declared in unstable class 'pkg.AnnotatedClass'">NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</warning>;
    <warning descr="'pkg.AnnotatedClass' is marked unstable">AnnotatedClass</warning>.<warning descr="'staticNonAnnotatedMethodInAnnotatedClass()' is declared in unstable class 'pkg.AnnotatedClass'">staticNonAnnotatedMethodInAnnotatedClass</warning>();
    <warning descr="'pkg.AnnotatedClass' is marked unstable">AnnotatedClass</warning> annotatedClassInstanceViaNonAnnotatedConstructor = new <warning descr="'AnnotatedClass()' is declared in unstable class 'pkg.AnnotatedClass'"><warning descr="'pkg.AnnotatedClass' is marked unstable">AnnotatedClass</warning></warning>();
    s = annotatedClassInstanceViaNonAnnotatedConstructor.<warning descr="'nonAnnotatedFieldInAnnotatedClass' is declared in unstable class 'pkg.AnnotatedClass'">nonAnnotatedFieldInAnnotatedClass</warning>;
    annotatedClassInstanceViaNonAnnotatedConstructor.<warning descr="'nonAnnotatedMethodInAnnotatedClass()' is declared in unstable class 'pkg.AnnotatedClass'">nonAnnotatedMethodInAnnotatedClass</warning>();
    s = <warning descr="'NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is declared in unstable class 'pkg.AnnotatedClass'">NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</warning>;
    <warning descr="'staticNonAnnotatedMethodInAnnotatedClass()' is declared in unstable class 'pkg.AnnotatedClass'">staticNonAnnotatedMethodInAnnotatedClass</warning>();

    s = <warning descr="'pkg.AnnotatedClass' is marked unstable">AnnotatedClass</warning>.<warning descr="'ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is marked unstable">ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</warning>;
    <warning descr="'pkg.AnnotatedClass' is marked unstable">AnnotatedClass</warning>.<warning descr="'staticAnnotatedMethodInAnnotatedClass()' is marked unstable">staticAnnotatedMethodInAnnotatedClass</warning>();
    <warning descr="'pkg.AnnotatedClass' is marked unstable">AnnotatedClass</warning> annotatedClassInstanceViaAnnotatedConstructor = new <warning descr="'AnnotatedClass(java.lang.String)' is marked unstable"><warning descr="'pkg.AnnotatedClass' is marked unstable">AnnotatedClass</warning></warning>("");
    s = annotatedClassInstanceViaAnnotatedConstructor.<warning descr="'annotatedFieldInAnnotatedClass' is marked unstable">annotatedFieldInAnnotatedClass</warning>;
    annotatedClassInstanceViaAnnotatedConstructor.<warning descr="'annotatedMethodInAnnotatedClass()' is marked unstable">annotatedMethodInAnnotatedClass</warning>();
    s = <warning descr="'ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is marked unstable">ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</warning>;
    <warning descr="'staticAnnotatedMethodInAnnotatedClass()' is marked unstable">staticAnnotatedMethodInAnnotatedClass</warning>();

    // ---------------------------------

    s = NonAnnotatedClass.NON_ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS;
    NonAnnotatedClass.staticNonAnnotatedMethodInNonAnnotatedClass();
    NonAnnotatedClass nonAnnotatedClassInstanceViaNonAnnotatedConstructor = new NonAnnotatedClass();
    s = nonAnnotatedClassInstanceViaNonAnnotatedConstructor.nonAnnotatedFieldInNonAnnotatedClass;
    nonAnnotatedClassInstanceViaNonAnnotatedConstructor.nonAnnotatedMethodInNonAnnotatedClass();
    s = NON_ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS;
    staticNonAnnotatedMethodInNonAnnotatedClass();

    s = NonAnnotatedClass.<warning descr="'ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS' is marked unstable">ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS</warning>;
    NonAnnotatedClass.<warning descr="'staticAnnotatedMethodInNonAnnotatedClass()' is marked unstable">staticAnnotatedMethodInNonAnnotatedClass</warning>();
    NonAnnotatedClass nonAnnotatedClassInstanceViaAnnotatedConstructor = new <warning descr="'NonAnnotatedClass(java.lang.String)' is marked unstable">NonAnnotatedClass</warning>("");
    s = nonAnnotatedClassInstanceViaAnnotatedConstructor.<warning descr="'annotatedFieldInNonAnnotatedClass' is marked unstable">annotatedFieldInNonAnnotatedClass</warning>;
    nonAnnotatedClassInstanceViaAnnotatedConstructor.<warning descr="'annotatedMethodInNonAnnotatedClass()' is marked unstable">annotatedMethodInNonAnnotatedClass</warning>();
    s = <warning descr="'ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS' is marked unstable">ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS</warning>;
    <warning descr="'staticAnnotatedMethodInNonAnnotatedClass()' is marked unstable">staticAnnotatedMethodInNonAnnotatedClass</warning>();

    // ---------------------------------

    <warning descr="'pkg.AnnotatedEnum' is marked unstable">AnnotatedEnum</warning> nonAnnotatedValueInAnnotatedEnum = <warning descr="'pkg.AnnotatedEnum' is marked unstable">AnnotatedEnum</warning>.<warning descr="'NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is declared in unstable enum 'pkg.AnnotatedEnum'">NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM</warning>;
    nonAnnotatedValueInAnnotatedEnum = <warning descr="'NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is declared in unstable enum 'pkg.AnnotatedEnum'">NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM</warning>;
    <warning descr="'pkg.AnnotatedEnum' is marked unstable">AnnotatedEnum</warning> annotatedValueInAnnotatedEnum = <warning descr="'pkg.AnnotatedEnum' is marked unstable">AnnotatedEnum</warning>.<warning descr="'ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is marked unstable">ANNOTATED_VALUE_IN_ANNOTATED_ENUM</warning>;
    annotatedValueInAnnotatedEnum = <warning descr="'ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is marked unstable">ANNOTATED_VALUE_IN_ANNOTATED_ENUM</warning>;

    NonAnnotatedEnum nonAnnotatedValueInNonAnnotatedEnum = NonAnnotatedEnum.NON_ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM;
    nonAnnotatedValueInNonAnnotatedEnum = NON_ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM;
    NonAnnotatedEnum annotatedValueInNonAnnotatedEnum = NonAnnotatedEnum.<warning descr="'ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM' is marked unstable">ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM</warning>;
    annotatedValueInNonAnnotatedEnum = <warning descr="'ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM' is marked unstable">ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM</warning>;
    
    // ---------------------------------

    @<warning descr="'pkg.AnnotatedAnnotation' is marked unstable">AnnotatedAnnotation</warning> class C1 {}
    @<warning descr="'pkg.AnnotatedAnnotation' is marked unstable">AnnotatedAnnotation</warning>(<warning descr="'nonAnnotatedAttributeInAnnotatedAnnotation' is declared in unstable @interface 'pkg.AnnotatedAnnotation'">nonAnnotatedAttributeInAnnotatedAnnotation</warning> = "123") class C2 {}
    @<warning descr="'pkg.AnnotatedAnnotation' is marked unstable">AnnotatedAnnotation</warning>(<warning descr="'annotatedAttributeInAnnotatedAnnotation' is marked unstable">annotatedAttributeInAnnotatedAnnotation</warning> = "123") class C3 {}
    @NonAnnotatedAnnotation class C4 {}
    @NonAnnotatedAnnotation(nonAnnotatedAttributeInNonAnnotatedAnnotation = "123") class C5 {}
    @NonAnnotatedAnnotation(<warning descr="'annotatedAttributeInNonAnnotatedAnnotation' is marked unstable">annotatedAttributeInNonAnnotatedAnnotation</warning> = "123") class C6 {}
  }
}

class DirectOverrideAnnotatedMethod extends NonAnnotatedClass {
  @Override
  public void <warning descr="Overridden method 'annotatedMethodInNonAnnotatedClass()' is marked unstable">annotatedMethodInNonAnnotatedClass</warning>() {}
}

class IndirectOverrideAnnotatedMethod extends DirectOverrideAnnotatedMethod {
  @Override
  public void annotatedMethodInNonAnnotatedClass() {}
}

class <warning descr="'AnnotatedClass()' is declared in unstable class 'pkg.AnnotatedClass'">DirectOverrideNonAnnotatedMethodInAnnotatedClass</warning> extends <warning descr="'pkg.AnnotatedClass' is marked unstable">AnnotatedClass</warning> {
  @Override
  public void <warning descr="Overridden method 'nonAnnotatedMethodInAnnotatedClass()' is declared in unstable class 'pkg.AnnotatedClass'">nonAnnotatedMethodInAnnotatedClass</warning>() {}
}

class <warning descr="'AnnotatedClass()' is declared in unstable class 'pkg.AnnotatedClass'">DirectOverrideAnnotatedMethodInAnnotatedClass</warning> extends <warning descr="'pkg.AnnotatedClass' is marked unstable">AnnotatedClass</warning> {
  @Override
  public void <warning descr="Overridden method 'annotatedMethodInAnnotatedClass()' is marked unstable">annotatedMethodInAnnotatedClass</warning>() {}
}

//No warning should be produced.

class WarningsOfExperimentalTypesInSignature {
  public void classUsage() {
    new <warning descr="'pkg.ClassWithExperimentalTypeInSignature' is unstable because its signature references unstable class 'pkg.AnnotatedClass'">ClassWithExperimentalTypeInSignature<<warning descr="'pkg.AnnotatedClass' is marked unstable">AnnotatedClass</warning>></warning>();
  }

  public void membersUsages(OwnerOfMembersWithExperimentalTypesInSignature owner) {
    Object field = owner.<warning descr="'field' is unstable because its signature references unstable class 'pkg.AnnotatedClass'">field</warning>;
    owner.<warning descr="'parameterType(pkg.AnnotatedClass)' is unstable because its signature references unstable class 'pkg.AnnotatedClass'">parameterType</warning>(null);
    owner.<warning descr="'returnType()' is unstable because its signature references unstable class 'pkg.AnnotatedClass'">returnType</warning>();

    Object fieldPkg = owner.<warning descr="'field' is unstable because its signature references unstable class 'pkg.AnnotatedClass'">field</warning>;
    owner.<warning descr="'parameterTypePkg(annotatedPkg.ClassInAnnotatedPkg)' is unstable because its signature references unstable class 'annotatedPkg.ClassInAnnotatedPkg'">parameterTypePkg</warning>(null);
    owner.<warning descr="'returnTypePkg()' is unstable because its signature references unstable class 'pkg.AnnotatedClass'">returnTypePkg</warning>();
  }
}