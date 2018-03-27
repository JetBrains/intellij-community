/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.codeInspection.bytecodeAnalysis.data;

import com.intellij.java.codeInspection.bytecodeAnalysis.ExpectContract;
import com.intellij.java.codeInspection.bytecodeAnalysis.ExpectNotNull;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.file.Files;

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
    use(o);
  }

  private static String use(@ExpectNotNull Object o) {
    System.out.println(o);
    return o.toString();
  }

  @ExpectContract(pure = true)
  static void v(Object o) {

  }

  @ExpectContract("null->null")
  static String toString1(Object o) {
    return o == null ? null : use(o);
  }

  @ExpectContract("null->!null")
  static String toString2(Object o) {
    return o == null ? "null" : use(o);
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

  interface MySupplier {
    String get();
  }

  @ExpectNotNull
  @ExpectContract(pure = true)
  public static MySupplier lambda(@ExpectNotNull String s) {
    return () -> s.trim();
  }

  @ExpectNotNull
  @ExpectContract(pure = true)
  public MySupplier lambdaNonStatic(@ExpectNotNull String s) {
    return () -> getThis().hashCode() + s.trim();
  }

  @ExpectNotNull
  public MySupplier lambdaBranching(@ExpectNotNull String s, String t, boolean b) {
    if(b) {
      System.out.println(s);
    } else {
      System.out.println(t);
    }
    return () -> s.trim();
  }

  @ExpectNotNull
  @ExpectContract(pure = true)
  public static MySupplier methodReference(@ExpectNotNull String s) {
    return s::trim;
  }

  @ExpectContract(pure = true, value="null,_->fail")
  public static void assertNotNull(@ExpectNotNull Object obj, String message) {
    if(obj == null) {
      throw new IllegalArgumentException(message);
    }
  }

  @ExpectContract(pure = true, value="false,_,_->fail;true,_,_->true")
  public static boolean assertTrue(boolean val, String message, int data) {
    if(!val) {
      throw new IllegalArgumentException(message+":"+data);
    }
    return val;
  }

  @ExpectContract(pure = true, value="true,_->fail;_,_->false")
  public static boolean assertFalse(boolean val, String message) {
    if(val) {
      throw new IllegalArgumentException(message);
    }
    return false;
  }

  @ExpectNotNull
  @ExpectContract(pure = true)
  public static long[] copyOfRange(@ExpectNotNull long[] arr, int from, int to) {
    int diff = to - from;
    if (diff < 0) {
      throw new IllegalArgumentException("Invalid arguments: " + from + '>' + to);
    }
    long[] copy = new long[diff];
    System.arraycopy(arr, from, copy, 0, Math.min(arr.length - from, diff));
    return copy;
  }

  @ExpectNotNull
  @ExpectContract(pure = true)
  public static long[] copyAndModify(@ExpectNotNull long[] input) {
    long[] copy = copyOfRange(input, 0, input.length);
    copy[0] = 1;
    set(copy);
    return copy;
  }

  private static void set(@ExpectNotNull long[] copy) {
    copy[1] = 2;
  }

  @ExpectContract(pure = true)
  public static <I, O> O[] copyOfRangeObject(@ExpectNotNull I[] arr, int from, int to, @ExpectNotNull Class<? extends O[]> newType) {
    int diff = to - from;
    if (diff < 0) {
      throw new IllegalArgumentException("Invalid arguments: " + from + '>' + to);
    }
    @SuppressWarnings("unchecked")
    O[] copy = (O[]) Array.newInstance(newType.getComponentType(), diff);
    //noinspection SuspiciousSystemArraycopy
    System.arraycopy(arr, from, copy, 0, Math.min(arr.length - from, diff));
    return copy;
  }

  @ExpectContract(value = "_->fail", pure = true)
  public static void callAlwaysFail(int x) {
    alwaysFail();
  }

  @ExpectContract(value = "_->fail", pure = true)
  public static void callAlwaysFailRef(String x) {
    callAlwaysFailTwoRefs(x, null);
  }

  @ExpectContract(value = "_,_->fail", pure = true)
  public static void callAlwaysFailTwoRefs(String x, String y) {
    alwaysFail();
  }

  @ExpectContract(value = "->fail", pure = true)
  private static void alwaysFail() {
    throw new UnsupportedOperationException();
  }

  @ExpectContract(value = "!null->null;null->!null", pure = true)
  static String invert(String x) {
    return x == null ? "empty" : null;
  }

  @ExpectContract(value = "false->true;true->false", pure = true)
  static boolean invertBool(boolean x) {
    return !x;
  }

  @ExpectContract(value = "_,true->true;null,_->true", pure=true)
  boolean checkTrueFail(Object a, boolean b) {
    if (a == null) return true;
    if (b) throw new RuntimeException();
    return b;
  }

  @ExpectNotNull
  String getStringNoTry(@ExpectNotNull String s) throws IOException {
    return String.valueOf(new FileReader(s.trim()).read());
  }

  String getStringTry(String s) {
    try {
      return String.valueOf(new FileReader(s.trim()).read());
    }
    catch (IOException ex) {
      return null;
    }
  }

  @ExpectContract("null->null")
  String getStringTryNPECatched(String s) {
    try {
      return String.valueOf(new FileReader(s.trim()).read());
    }
    catch (Exception ex) {
      return null;
    }
  }

  @ExpectContract(pure = true)
  void testThrow(@ExpectNotNull String s) {
    if(s.isEmpty()) {
      throw new IllegalArgumentException();
    }
  }

  @ExpectContract("!null->!null;null->null")
  String testCatchReturn(String s) {
    try {
      Integer.parseInt(s);
    }
    catch (NumberFormatException ex) {
      System.out.println("exception!");
    }
    return s;
  }

  boolean testCatchBool(File file) {
    try {
      Files.createDirectories(file.toPath());
      return true;
    }
    catch (IOException ignored) {

    }
    return false;
  }

  @ExpectContract("null->false")
  boolean testCatchBool2(File file) {
    try {
      Files.createDirectories(file.toPath());
      return true;
    }
    catch (Throwable ignored) {

    }
    return false;
  }

  @ExpectContract(pure = true)
  String[] replaceFirstWithNull(@ExpectNotNull String[] arr) {
    String[] res = arr.clone();
    res[0] = null;
    return res;
  }
}
