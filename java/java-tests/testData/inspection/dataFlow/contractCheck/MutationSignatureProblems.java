package org.jetbrains.annotations;

import java.lang.annotation.*;
import java.util.*;

@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
@interface Contract {
  String value() default "";
  boolean pure() default false;
  String mutates() default "";
}

class Test {
  @Contract(mutates = <warning descr="Static method cannot mutate 'this'">"this"</warning>)
  public static void test1(List<String> list) {}

  @Contract(mutates = <warning descr="Reference to parameter #3 is invalid">"param3"</warning>)
  public static void test2(List<String> list) {}

  @Contract(mutates = <warning descr="Invalid token: blahblahblah; supported are 'this', 'param1', 'param2', etc.">"blahblahblah"</warning>)
  public static void test3(List<String> list) {}

  @Contract(mutates = "param")
  public static void test4(List<String> list) {}

  @Contract(mutates = <warning descr="Pure method cannot have mutation contract">"param"</warning>, pure = true)
  public static void test5(List<String> list) {}

  @Contract(mutates = "param", pure = false)
  public static void test6(List<String> list) {}

  @Contract(mutates = "", pure = true)
  public static void test7(List<String> list) {}

  @Contract(mutates = <warning descr="Parameter #1 has immutable type 'String'">"param1"</warning>)
  public static void test8(String s, int i, List<String> list) {}

  @Contract(mutates = <warning descr="Parameter #2 has immutable type 'int'">"param2"</warning>)
  public static void test9(String s, int i, List<String> list) {}

  @Contract(mutates = "param3")
  public static void test10(String s, int i, List<String> list) {}
}