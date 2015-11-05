/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.bytecodeAnalysis.data;

import com.intellij.codeInspection.bytecodeAnalysis.ExpectContract;
import com.intellij.codeInspection.bytecodeAnalysis.ExpectNotNull;

/**
 * @author lambdamix
 */
public class Test01 {
  static void f(@ExpectNotNull Object o1, @ExpectNotNull Object o2) {
    if (o1 == null) throw new NullPointerException();
    else s(o2, o2);
  }

  static void g(@ExpectNotNull Object o, boolean b) {
    if (b) f(o, o);
    else s(o, o);
  }

  static void s(@ExpectNotNull Object o1, Object o2) {
    t(o1);
    v(o2);
  }

  static void t(@ExpectNotNull Object o) {
    o.toString();
  }

  @ExpectContract(pure = true)
  static void v(Object o) {

  }

  @ExpectContract("null->null")
  static String toString1(Object o) {
    return o == null ? null : o.toString();
  }

  @ExpectContract("null->!null")
  static String toString2(Object o) {
    return o == null ? "null" : o.toString();
  }

  @ExpectContract(pure = true)
  @ExpectNotNull
  static String constantString() {
    return "s";
  }

  @ExpectContract(value = "!null->!null;null->null", pure = true)
  static String idString(String s) {
    return s;
  }

  @ExpectContract(pure = true)
  @ExpectNotNull
  public Test01 getThis() {
    return this;
  }

  @ExpectContract(pure = true)
  @ExpectNotNull
  protected Test01 createRoot() {
    return new Test01();
  }

  @ExpectContract(value = "!null->false;null->true", pure = true)
  static boolean isNull(Object o) {
    return o == null;
  }

  @ExpectContract(value = "!null->true;null->false", pure = true)
  static boolean isNotNull(Object o) {
    return !isNull(o);
  }

}
