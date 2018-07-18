// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import pkg.<warning descr="'ExperimentalClass' is marked unstable">ExperimentalClass</warning>;
import static pkg.<warning descr="'ExperimentalClass' is marked unstable">ExperimentalClass</warning>.NON_EXPERIMENTAL_CONSTANT_IN_EXPERIMENTAL_CLASS;
import static pkg.<warning descr="'ExperimentalClass' is marked unstable">ExperimentalClass</warning>.staticNonExperimentalMethodInExperimentalClass;
import static pkg.<warning descr="'ExperimentalClass' is marked unstable">ExperimentalClass</warning>.<warning descr="'EXPERIMENTAL_CONSTANT_IN_EXPERIMENTAL_CLASS' is marked unstable">EXPERIMENTAL_CONSTANT_IN_EXPERIMENTAL_CLASS</warning>;
import static pkg.<warning descr="'ExperimentalClass' is marked unstable">ExperimentalClass</warning>.<warning descr="'staticExperimentalMethodInExperimentalClass' is marked unstable">staticExperimentalMethodInExperimentalClass</warning>;

import pkg.NonExperimentalClass;
import static pkg.NonExperimentalClass.NON_EXPERIMENTAL_CONSTANT_IN_NON_EXPERIMENTAL_CLASS;
import static pkg.NonExperimentalClass.staticNonExperimentalMethodInNonExperimentalClass;
import static pkg.NonExperimentalClass.<warning descr="'EXPERIMENTAL_CONSTANT_IN_NON_EXPERIMENTAL_CLASS' is marked unstable">EXPERIMENTAL_CONSTANT_IN_NON_EXPERIMENTAL_CLASS</warning>;
import static pkg.NonExperimentalClass.<warning descr="'staticExperimentalMethodInNonExperimentalClass' is marked unstable">staticExperimentalMethodInNonExperimentalClass</warning>;

import pkg.<warning descr="'ExperimentalEnum' is marked unstable">ExperimentalEnum</warning>;
import pkg.NonExperimentalEnum;
import static pkg.<warning descr="'ExperimentalEnum' is marked unstable">ExperimentalEnum</warning>.NON_EXPERIMENTAL_VALUE_IN_EXPERIMENTAL_ENUM;
import static pkg.<warning descr="'ExperimentalEnum' is marked unstable">ExperimentalEnum</warning>.<warning descr="'EXPERIMENTAL_VALUE_IN_EXPERIMENTAL_ENUM' is marked unstable">EXPERIMENTAL_VALUE_IN_EXPERIMENTAL_ENUM</warning>;
import static pkg.NonExperimentalEnum.NON_EXPERIMENTAL_VALUE_IN_NON_EXPERIMENTAL_ENUM;
import static pkg.NonExperimentalEnum.<warning descr="'EXPERIMENTAL_VALUE_IN_NON_EXPERIMENTAL_ENUM' is marked unstable">EXPERIMENTAL_VALUE_IN_NON_EXPERIMENTAL_ENUM</warning>;

import pkg.<warning descr="'ExperimentalAnnotation' is marked unstable">ExperimentalAnnotation</warning>;
import pkg.NonExperimentalAnnotation;

import <warning descr="'unstablePkg' is marked unstable">unstablePkg</warning>.ClassInUnstablePkg;

public class UnstableElementsTest {
  public void test() {
    String s = <warning descr="'ExperimentalClass' is marked unstable">ExperimentalClass</warning>.NON_EXPERIMENTAL_CONSTANT_IN_EXPERIMENTAL_CLASS;
    <warning descr="'ExperimentalClass' is marked unstable">ExperimentalClass</warning>.staticNonExperimentalMethodInExperimentalClass();
    <warning descr="'ExperimentalClass' is marked unstable">ExperimentalClass</warning> experimentalClassInstanceViaNonExperimentalConstructor = new ExperimentalClass();
    s = experimentalClassInstanceViaNonExperimentalConstructor.nonExperimentalFieldInExperimentalClass;
    experimentalClassInstanceViaNonExperimentalConstructor.nonExperimentalMethodInExperimentalClass();
    s = NON_EXPERIMENTAL_CONSTANT_IN_EXPERIMENTAL_CLASS;
    staticNonExperimentalMethodInExperimentalClass();

    s = <warning descr="'ExperimentalClass' is marked unstable">ExperimentalClass</warning>.<warning descr="'EXPERIMENTAL_CONSTANT_IN_EXPERIMENTAL_CLASS' is marked unstable">EXPERIMENTAL_CONSTANT_IN_EXPERIMENTAL_CLASS</warning>;
    <warning descr="'ExperimentalClass' is marked unstable">ExperimentalClass</warning>.<warning descr="'staticExperimentalMethodInExperimentalClass' is marked unstable">staticExperimentalMethodInExperimentalClass</warning>();
    <warning descr="'ExperimentalClass' is marked unstable">ExperimentalClass</warning> experimentalClassInstanceViaExperimentalConstructor = new <warning descr="'ExperimentalClass' is marked unstable">ExperimentalClass</warning>("");
    s = experimentalClassInstanceViaExperimentalConstructor.<warning descr="'experimentalFieldInExperimentalClass' is marked unstable">experimentalFieldInExperimentalClass</warning>;
    experimentalClassInstanceViaExperimentalConstructor.<warning descr="'experimentalMethodInExperimentalClass' is marked unstable">experimentalMethodInExperimentalClass</warning>();
    s = <warning descr="'EXPERIMENTAL_CONSTANT_IN_EXPERIMENTAL_CLASS' is marked unstable">EXPERIMENTAL_CONSTANT_IN_EXPERIMENTAL_CLASS</warning>;
    <warning descr="'staticExperimentalMethodInExperimentalClass' is marked unstable">staticExperimentalMethodInExperimentalClass</warning>();

    // ---------------------------------

    s = NonExperimentalClass.NON_EXPERIMENTAL_CONSTANT_IN_NON_EXPERIMENTAL_CLASS;
    NonExperimentalClass.staticNonExperimentalMethodInNonExperimentalClass();
    NonExperimentalClass nonExperimentalClassInstanceViaNonExperimentalConstructor = new NonExperimentalClass();
    s = nonExperimentalClassInstanceViaNonExperimentalConstructor.nonExperimentalFieldInNonExperimentalClass;
    nonExperimentalClassInstanceViaNonExperimentalConstructor.nonExperimentalMethodInNonExperimentalClass();
    s = NON_EXPERIMENTAL_CONSTANT_IN_NON_EXPERIMENTAL_CLASS;
    staticNonExperimentalMethodInNonExperimentalClass();

    s = NonExperimentalClass.<warning descr="'EXPERIMENTAL_CONSTANT_IN_NON_EXPERIMENTAL_CLASS' is marked unstable">EXPERIMENTAL_CONSTANT_IN_NON_EXPERIMENTAL_CLASS</warning>;
    NonExperimentalClass.<warning descr="'staticExperimentalMethodInNonExperimentalClass' is marked unstable">staticExperimentalMethodInNonExperimentalClass</warning>();
    NonExperimentalClass nonExperimentalClassInstanceViaExperimentalConstructor = new <warning descr="'NonExperimentalClass' is marked unstable">NonExperimentalClass</warning>("");
    s = nonExperimentalClassInstanceViaExperimentalConstructor.<warning descr="'experimentalFieldInNonExperimentalClass' is marked unstable">experimentalFieldInNonExperimentalClass</warning>;
    nonExperimentalClassInstanceViaExperimentalConstructor.<warning descr="'experimentalMethodInNonExperimentalClass' is marked unstable">experimentalMethodInNonExperimentalClass</warning>();
    s = <warning descr="'EXPERIMENTAL_CONSTANT_IN_NON_EXPERIMENTAL_CLASS' is marked unstable">EXPERIMENTAL_CONSTANT_IN_NON_EXPERIMENTAL_CLASS</warning>;
    <warning descr="'staticExperimentalMethodInNonExperimentalClass' is marked unstable">staticExperimentalMethodInNonExperimentalClass</warning>();

    // ---------------------------------

    <warning descr="'ExperimentalEnum' is marked unstable">ExperimentalEnum</warning> nonExperimentalValueInExperimentalEnum = <warning descr="'ExperimentalEnum' is marked unstable">ExperimentalEnum</warning>.NON_EXPERIMENTAL_VALUE_IN_EXPERIMENTAL_ENUM;
    nonExperimentalValueInExperimentalEnum = NON_EXPERIMENTAL_VALUE_IN_EXPERIMENTAL_ENUM;
    <warning descr="'ExperimentalEnum' is marked unstable">ExperimentalEnum</warning> experimentalValueInExperimentalEnum = <warning descr="'ExperimentalEnum' is marked unstable">ExperimentalEnum</warning>.<warning descr="'EXPERIMENTAL_VALUE_IN_EXPERIMENTAL_ENUM' is marked unstable">EXPERIMENTAL_VALUE_IN_EXPERIMENTAL_ENUM</warning>;
    experimentalValueInExperimentalEnum = <warning descr="'EXPERIMENTAL_VALUE_IN_EXPERIMENTAL_ENUM' is marked unstable">EXPERIMENTAL_VALUE_IN_EXPERIMENTAL_ENUM</warning>;

    NonExperimentalEnum nonExperimentalValueInNonExperimentalEnum = NonExperimentalEnum.NON_EXPERIMENTAL_VALUE_IN_NON_EXPERIMENTAL_ENUM;
    nonExperimentalValueInNonExperimentalEnum = NON_EXPERIMENTAL_VALUE_IN_NON_EXPERIMENTAL_ENUM;
    NonExperimentalEnum experimentalValueInNonExperimentalEnum = NonExperimentalEnum.<warning descr="'EXPERIMENTAL_VALUE_IN_NON_EXPERIMENTAL_ENUM' is marked unstable">EXPERIMENTAL_VALUE_IN_NON_EXPERIMENTAL_ENUM</warning>;
    experimentalValueInNonExperimentalEnum = <warning descr="'EXPERIMENTAL_VALUE_IN_NON_EXPERIMENTAL_ENUM' is marked unstable">EXPERIMENTAL_VALUE_IN_NON_EXPERIMENTAL_ENUM</warning>;
    
    // ---------------------------------

    @<warning descr="'ExperimentalAnnotation' is marked unstable">ExperimentalAnnotation</warning> class C1 {}
    @<warning descr="'ExperimentalAnnotation' is marked unstable">ExperimentalAnnotation</warning>(nonExperimentalAttributeInExperimentalAnnotation = "123") class C2 {}
    @<warning descr="'ExperimentalAnnotation' is marked unstable">ExperimentalAnnotation</warning>(<warning descr="'experimentalAttributeInExperimentalAnnotation' is marked unstable">experimentalAttributeInExperimentalAnnotation</warning> = "123") class C3 {}
    @NonExperimentalAnnotation class C4 {}
    @NonExperimentalAnnotation(nonExperimentalAttributeInNonExperimentalAnnotation = "123") class C5 {}
    @NonExperimentalAnnotation(<warning descr="'experimentalAttributeInNonExperimentalAnnotation' is marked unstable">experimentalAttributeInNonExperimentalAnnotation</warning> = "123") class C6 {}
  }
}