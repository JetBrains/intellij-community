// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import pkg.AnnotatedClass;
import static pkg.AnnotatedClass.NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS;
import static pkg.AnnotatedClass.staticNonAnnotatedMethodInAnnotatedClass;
import static pkg.AnnotatedClass.ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS;
import static pkg.AnnotatedClass.staticAnnotatedMethodInAnnotatedClass;

import pkg.NonAnnotatedClass;
import static pkg.NonAnnotatedClass.NON_ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS;
import static pkg.NonAnnotatedClass.staticNonAnnotatedMethodInNonAnnotatedClass;
import static pkg.NonAnnotatedClass.ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS;
import static pkg.NonAnnotatedClass.staticAnnotatedMethodInNonAnnotatedClass;

import pkg.AnnotatedEnum;
import pkg.NonAnnotatedEnum;
import static pkg.AnnotatedEnum.NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM;
import static pkg.AnnotatedEnum.ANNOTATED_VALUE_IN_ANNOTATED_ENUM;
import static pkg.NonAnnotatedEnum.NON_ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM;
import static pkg.NonAnnotatedEnum.ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM;

import pkg.AnnotatedAnnotation;
import pkg.NonAnnotatedAnnotation;

import annotatedPkg.ClassInAnnotatedPkg;

public class UnstableElementsIgnoreImportsTest {
  public void test() {
    String s = <warning descr="'AnnotatedClass' is marked unstable">AnnotatedClass</warning>.NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS;
    <warning descr="'AnnotatedClass' is marked unstable">AnnotatedClass</warning>.staticNonAnnotatedMethodInAnnotatedClass();
    <warning descr="'AnnotatedClass' is marked unstable">AnnotatedClass</warning> annotatedClassInstanceViaNonAnnotatedConstructor = new AnnotatedClass();
    s = annotatedClassInstanceViaNonAnnotatedConstructor.nonAnnotatedFieldInAnnotatedClass;
    annotatedClassInstanceViaNonAnnotatedConstructor.nonAnnotatedMethodInAnnotatedClass();
    s = NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS;
    staticNonAnnotatedMethodInAnnotatedClass();

    s = <warning descr="'AnnotatedClass' is marked unstable">AnnotatedClass</warning>.<warning descr="'ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is marked unstable">ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</warning>;
    <warning descr="'AnnotatedClass' is marked unstable">AnnotatedClass</warning>.<warning descr="'staticAnnotatedMethodInAnnotatedClass' is marked unstable">staticAnnotatedMethodInAnnotatedClass</warning>();
    <warning descr="'AnnotatedClass' is marked unstable">AnnotatedClass</warning> annotatedClassInstanceViaAnnotatedConstructor = new <warning descr="'AnnotatedClass' is marked unstable">AnnotatedClass</warning>("");
    s = annotatedClassInstanceViaAnnotatedConstructor.<warning descr="'annotatedFieldInAnnotatedClass' is marked unstable">annotatedFieldInAnnotatedClass</warning>;
    annotatedClassInstanceViaAnnotatedConstructor.<warning descr="'annotatedMethodInAnnotatedClass' is marked unstable">annotatedMethodInAnnotatedClass</warning>();
    s = <warning descr="'ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is marked unstable">ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</warning>;
    <warning descr="'staticAnnotatedMethodInAnnotatedClass' is marked unstable">staticAnnotatedMethodInAnnotatedClass</warning>();

    // ---------------------------------

    s = NonAnnotatedClass.NON_ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS;
    NonAnnotatedClass.staticNonAnnotatedMethodInNonAnnotatedClass();
    NonAnnotatedClass nonAnnotatedClassInstanceViaNonAnnotatedConstructor = new NonAnnotatedClass();
    s = nonAnnotatedClassInstanceViaNonAnnotatedConstructor.nonAnnotatedFieldInNonAnnotatedClass;
    nonAnnotatedClassInstanceViaNonAnnotatedConstructor.nonAnnotatedMethodInNonAnnotatedClass();
    s = NON_ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS;
    staticNonAnnotatedMethodInNonAnnotatedClass();

    s = NonAnnotatedClass.<warning descr="'ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS' is marked unstable">ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS</warning>;
    NonAnnotatedClass.<warning descr="'staticAnnotatedMethodInNonAnnotatedClass' is marked unstable">staticAnnotatedMethodInNonAnnotatedClass</warning>();
    NonAnnotatedClass nonAnnotatedClassInstanceViaAnnotatedConstructor = new <warning descr="'NonAnnotatedClass' is marked unstable">NonAnnotatedClass</warning>("");
    s = nonAnnotatedClassInstanceViaAnnotatedConstructor.<warning descr="'annotatedFieldInNonAnnotatedClass' is marked unstable">annotatedFieldInNonAnnotatedClass</warning>;
    nonAnnotatedClassInstanceViaAnnotatedConstructor.<warning descr="'annotatedMethodInNonAnnotatedClass' is marked unstable">annotatedMethodInNonAnnotatedClass</warning>();
    s = <warning descr="'ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS' is marked unstable">ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS</warning>;
    <warning descr="'staticAnnotatedMethodInNonAnnotatedClass' is marked unstable">staticAnnotatedMethodInNonAnnotatedClass</warning>();

    // ---------------------------------

    <warning descr="'AnnotatedEnum' is marked unstable">AnnotatedEnum</warning> nonAnnotatedValueInAnnotatedEnum = <warning descr="'AnnotatedEnum' is marked unstable">AnnotatedEnum</warning>.NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM;
    nonAnnotatedValueInAnnotatedEnum = NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM;
    <warning descr="'AnnotatedEnum' is marked unstable">AnnotatedEnum</warning> annotatedValueInAnnotatedEnum = <warning descr="'AnnotatedEnum' is marked unstable">AnnotatedEnum</warning>.<warning descr="'ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is marked unstable">ANNOTATED_VALUE_IN_ANNOTATED_ENUM</warning>;
    annotatedValueInAnnotatedEnum = <warning descr="'ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is marked unstable">ANNOTATED_VALUE_IN_ANNOTATED_ENUM</warning>;

    NonAnnotatedEnum nonAnnotatedValueInNonAnnotatedEnum = NonAnnotatedEnum.NON_ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM;
    nonAnnotatedValueInNonAnnotatedEnum = NON_ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM;
    NonAnnotatedEnum annotatedValueInNonAnnotatedEnum = NonAnnotatedEnum.<warning descr="'ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM' is marked unstable">ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM</warning>;
    annotatedValueInNonAnnotatedEnum = <warning descr="'ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM' is marked unstable">ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM</warning>;
    
    // ---------------------------------

     @<warning descr="'AnnotatedAnnotation' is marked unstable">AnnotatedAnnotation</warning> class C1 {}
    @<warning descr="'AnnotatedAnnotation' is marked unstable">AnnotatedAnnotation</warning>(nonAnnotatedAttributeInAnnotatedAnnotation = "123") class C2 {}
    @<warning descr="'AnnotatedAnnotation' is marked unstable">AnnotatedAnnotation</warning>(<warning descr="'annotatedAttributeInAnnotatedAnnotation' is marked unstable">annotatedAttributeInAnnotatedAnnotation</warning> = "123") class C3 {}
    @NonAnnotatedAnnotation class C4 {}
    @NonAnnotatedAnnotation(nonAnnotatedAttributeInNonAnnotatedAnnotation = "123") class C5 {}
    @NonAnnotatedAnnotation(<warning descr="'annotatedAttributeInNonAnnotatedAnnotation' is marked unstable">annotatedAttributeInNonAnnotatedAnnotation</warning> = "123") class C6 {}
  }
}