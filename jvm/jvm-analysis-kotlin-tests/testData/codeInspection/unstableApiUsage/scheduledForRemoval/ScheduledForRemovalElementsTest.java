// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import pkg.<error descr="'pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</error>;
import pkg.<error descr="'pkg.ClassWithScheduledForRemovalTypeInSignature' is scheduled for removal because its signature references class 'pkg.AnnotatedClass' scheduled for removal in version 123.456">ClassWithScheduledForRemovalTypeInSignature</error>;
import pkg.OwnerOfMembersWithScheduledForRemovalTypesInSignature;
import static pkg.<error descr="'pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</error>.<error descr="'NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is declared in class 'pkg.AnnotatedClass' scheduled for removal in version 123.456">NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</error>;
import static pkg.<error descr="'pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</error>.<error descr="'staticNonAnnotatedMethodInAnnotatedClass()' is declared in class 'pkg.AnnotatedClass' scheduled for removal in version 123.456">staticNonAnnotatedMethodInAnnotatedClass</error>;
import static pkg.<error descr="'pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</error>.<error descr="'ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is scheduled for removal in version 123.456">ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</error>;
import static pkg.<error descr="'pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</error>.<error descr="'staticAnnotatedMethodInAnnotatedClass()' is scheduled for removal in version 123.456">staticAnnotatedMethodInAnnotatedClass</error>;

import pkg.NonAnnotatedClass;
import static pkg.NonAnnotatedClass.NON_ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS;
import static pkg.NonAnnotatedClass.staticNonAnnotatedMethodInNonAnnotatedClass;
import static pkg.NonAnnotatedClass.<error descr="'ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS' is scheduled for removal in version 123.456">ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS</error>;
import static pkg.NonAnnotatedClass.<error descr="'staticAnnotatedMethodInNonAnnotatedClass()' is scheduled for removal in version 123.456">staticAnnotatedMethodInNonAnnotatedClass</error>;

import pkg.<error descr="'pkg.AnnotatedEnum' is scheduled for removal in version 123.456">AnnotatedEnum</error>;
import pkg.NonAnnotatedEnum;
import static pkg.<error descr="'pkg.AnnotatedEnum' is scheduled for removal in version 123.456">AnnotatedEnum</error>.<error descr="'NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is declared in enum 'pkg.AnnotatedEnum' scheduled for removal in version 123.456">NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM</error>;
import static pkg.<error descr="'pkg.AnnotatedEnum' is scheduled for removal in version 123.456">AnnotatedEnum</error>.<error descr="'ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is scheduled for removal in version 123.456">ANNOTATED_VALUE_IN_ANNOTATED_ENUM</error>;
import static pkg.NonAnnotatedEnum.NON_ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM;
import static pkg.NonAnnotatedEnum.<error descr="'ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM' is scheduled for removal in version 123.456">ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM</error>;

import pkg.<error descr="'pkg.AnnotatedAnnotation' is scheduled for removal in version 123.456">AnnotatedAnnotation</error>;
import pkg.NonAnnotatedAnnotation;

import <error descr="'annotatedPkg' is scheduled for removal in version 123.456">annotatedPkg</error>.<error descr="'annotatedPkg.ClassInAnnotatedPkg' is declared in package 'annotatedPkg' scheduled for removal in version 123.456">ClassInAnnotatedPkg</error>;

public class ScheduledForRemovalElementsTest {
  public void test() {
    String s = <error descr="'pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</error>.<error descr="'NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is declared in class 'pkg.AnnotatedClass' scheduled for removal in version 123.456">NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</error>;
    <error descr="'pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</error>.<error descr="'staticNonAnnotatedMethodInAnnotatedClass()' is declared in class 'pkg.AnnotatedClass' scheduled for removal in version 123.456">staticNonAnnotatedMethodInAnnotatedClass</error>();
    <error descr="'pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</error> annotatedClassInstanceViaNonAnnotatedConstructor = new <error descr="'AnnotatedClass()' is declared in class 'pkg.AnnotatedClass' scheduled for removal in version 123.456"><error descr="'pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</error></error>();
    s = annotatedClassInstanceViaNonAnnotatedConstructor.<error descr="'nonAnnotatedFieldInAnnotatedClass' is declared in class 'pkg.AnnotatedClass' scheduled for removal in version 123.456">nonAnnotatedFieldInAnnotatedClass</error>;
    annotatedClassInstanceViaNonAnnotatedConstructor.<error descr="'nonAnnotatedMethodInAnnotatedClass()' is declared in class 'pkg.AnnotatedClass' scheduled for removal in version 123.456">nonAnnotatedMethodInAnnotatedClass</error>();
    s = <error descr="'NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is declared in class 'pkg.AnnotatedClass' scheduled for removal in version 123.456">NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</error>;
    <error descr="'staticNonAnnotatedMethodInAnnotatedClass()' is declared in class 'pkg.AnnotatedClass' scheduled for removal in version 123.456">staticNonAnnotatedMethodInAnnotatedClass</error>();

    s = <error descr="'pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</error>.<error descr="'ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS' is scheduled for removal in version 123.456">ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS</error>;
    <error descr="'pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</error>.<error descr="'staticAnnotatedMethodInAnnotatedClass()' is scheduled for removal in version 123.456">staticAnnotatedMethodInAnnotatedClass</error>();
    <error descr="'pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</error> annotatedClassInstanceViaAnnotatedConstructor = new <error descr="'AnnotatedClass(java.lang.String)' is scheduled for removal in version 123.456"><error descr="'pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</error></error>("");
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

    <error descr="'pkg.AnnotatedEnum' is scheduled for removal in version 123.456">AnnotatedEnum</error> nonAnnotatedValueInAnnotatedEnum = <error descr="'pkg.AnnotatedEnum' is scheduled for removal in version 123.456">AnnotatedEnum</error>.<error descr="'NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is declared in enum 'pkg.AnnotatedEnum' scheduled for removal in version 123.456">NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM</error>;
    nonAnnotatedValueInAnnotatedEnum = <error descr="'NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is declared in enum 'pkg.AnnotatedEnum' scheduled for removal in version 123.456">NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM</error>;
    <error descr="'pkg.AnnotatedEnum' is scheduled for removal in version 123.456">AnnotatedEnum</error> annotatedValueInAnnotatedEnum = <error descr="'pkg.AnnotatedEnum' is scheduled for removal in version 123.456">AnnotatedEnum</error>.<error descr="'ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is scheduled for removal in version 123.456">ANNOTATED_VALUE_IN_ANNOTATED_ENUM</error>;
    annotatedValueInAnnotatedEnum = <error descr="'ANNOTATED_VALUE_IN_ANNOTATED_ENUM' is scheduled for removal in version 123.456">ANNOTATED_VALUE_IN_ANNOTATED_ENUM</error>;

    NonAnnotatedEnum nonAnnotatedValueInNonAnnotatedEnum = NonAnnotatedEnum.NON_ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM;
    nonAnnotatedValueInNonAnnotatedEnum = NON_ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM;
    NonAnnotatedEnum annotatedValueInNonAnnotatedEnum = NonAnnotatedEnum.<error descr="'ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM' is scheduled for removal in version 123.456">ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM</error>;
    annotatedValueInNonAnnotatedEnum = <error descr="'ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM' is scheduled for removal in version 123.456">ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM</error>;
    
    // ---------------------------------

    @<error descr="'pkg.AnnotatedAnnotation' is scheduled for removal in version 123.456">AnnotatedAnnotation</error> class C1 {}
    @<error descr="'pkg.AnnotatedAnnotation' is scheduled for removal in version 123.456">AnnotatedAnnotation</error>(<error descr="'nonAnnotatedAttributeInAnnotatedAnnotation' is declared in annotation 'pkg.AnnotatedAnnotation' scheduled for removal in version 123.456">nonAnnotatedAttributeInAnnotatedAnnotation</error> = "123") class C2 {}
    @<error descr="'pkg.AnnotatedAnnotation' is scheduled for removal in version 123.456">AnnotatedAnnotation</error>(<error descr="'annotatedAttributeInAnnotatedAnnotation' is scheduled for removal in version 123.456">annotatedAttributeInAnnotatedAnnotation</error> = "123") class C3 {}
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
    new <error descr="'pkg.ClassWithScheduledForRemovalTypeInSignature' is scheduled for removal because its signature references class 'pkg.AnnotatedClass' scheduled for removal in version 123.456">ClassWithScheduledForRemovalTypeInSignature<<error descr="'pkg.AnnotatedClass' is scheduled for removal in version 123.456">AnnotatedClass</error>></error>();
  }

  public void membersUsages(OwnerOfMembersWithScheduledForRemovalTypesInSignature owner) {
    Object field = owner.<error descr="'field' is scheduled for removal because its signature references class 'pkg.AnnotatedClass' scheduled for removal in version 123.456">field</error>;
    owner.<error descr="'parameterType(pkg.AnnotatedClass)' is scheduled for removal because its signature references class 'pkg.AnnotatedClass' scheduled for removal in version 123.456">parameterType</error>(null);
    owner.<error descr="'returnType()' is scheduled for removal because its signature references class 'pkg.AnnotatedClass' scheduled for removal in version 123.456">returnType</error>();

    Object fieldPkg = owner.<error descr="'field' is scheduled for removal because its signature references class 'pkg.AnnotatedClass' scheduled for removal in version 123.456">field</error>;
    owner.<error descr="'parameterTypePkg(annotatedPkg.ClassInAnnotatedPkg)' is scheduled for removal because its signature references class 'annotatedPkg.ClassInAnnotatedPkg' scheduled for removal in version 123.456">parameterTypePkg</error>(null);
    owner.<error descr="'returnTypePkg()' is scheduled for removal because its signature references class 'pkg.AnnotatedClass' scheduled for removal in version 123.456">returnTypePkg</error>();
  }
}