// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.aaaNonNlsTest;

import org.jetbrains.annotations.NonNls;

@SuppressWarnings({"ResultOfMethodCallIgnored", "MethodMayBeStatic"})
public class NonNlsCases {
  String plainField = "3";
  @NonNls String nonNlsField = "4";

  String initializedPlainField = "1".<warning descr="'String.toUpperCase()' called without specifying a Locale using internationalized strings">toUpperCase</warning>() + "2".<warning descr="'String.toLowerCase()' called without specifying a Locale using internationalized strings">toLowerCase</warning>();
  @NonNls String initializedNonNlsField = "1".toUpperCase() + "2".toLowerCase();
  @NonNls String initializedNonNlsField2 = initializedNonNlsField.toUpperCase();


  public void checkFields() {
    plainField.<warning descr="'String.toUpperCase()' called without specifying a Locale using internationalized strings">toUpperCase</warning>();
    String s = plainField.<warning descr="'String.toUpperCase()' called without specifying a Locale using internationalized strings">toUpperCase</warning>();
    nonNlsField.toUpperCase();
    String ss = nonNlsField.toUpperCase();

    plainField.concat("1".<warning descr="'String.toUpperCase()' called without specifying a Locale using internationalized strings">toUpperCase</warning>());
    nonNlsField.concat("1".toUpperCase());
  }


  public void checkVariables() {
    String plain = "123";
    @NonNls String nonNls = "123";

    plain.<warning descr="'String.toUpperCase()' called without specifying a Locale using internationalized strings">toUpperCase</warning>();
    nonNls.toUpperCase();

    plain.concat("1".<warning descr="'String.toUpperCase()' called without specifying a Locale using internationalized strings">toUpperCase</warning>());
    nonNls.concat("1".toUpperCase());

    @NonNls String s = "123".toUpperCase();
    s.toLowerCase();
    String ss = "123".<warning descr="'String.toUpperCase()' called without specifying a Locale using internationalized strings">toUpperCase</warning>();
    ss.<warning descr="'String.toLowerCase()' called without specifying a Locale using internationalized strings">toLowerCase</warning>();
  }


  public void checkParameters(@NonNls String nonNlsParam, String plainParam) {
    nonNlsParam.toUpperCase();
    plainParam.<warning descr="'String.toUpperCase()' called without specifying a Locale using internationalized strings">toUpperCase</warning>();

    plainParam.concat("1".<warning descr="'String.toUpperCase()' called without specifying a Locale using internationalized strings">toUpperCase</warning>());
    nonNlsParam.concat("1".toUpperCase());
  }


  public void checkMethods() {
    nonNlsMethod().toUpperCase();
    plainMethod().<warning descr="'String.toUpperCase()' called without specifying a Locale using internationalized strings">toUpperCase</warning>();

    Clazz clazzInstance = new Clazz();
    clazzInstance.nestedNonNlsMethod().toUpperCase();
    clazzInstance.nestedPlainMethod().<warning descr="'String.toUpperCase()' called without specifying a Locale using internationalized strings">toUpperCase</warning>();
  }



  @NonNls
  String nonNlsMethod() { return ""; }
  String plainMethod() { return ""; }

  static class Clazz {
    @NonNls
    String nestedNonNlsMethod() { return ""; }
    String nestedPlainMethod() { return ""; }
  }


  @NonNls
  static class NonNlsClass {
    String s = "123".toUpperCase();
  }
}
