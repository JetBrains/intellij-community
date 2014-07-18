/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

@SuppressWarnings({"UnusedDeclaration"})
class C {
  // from http://download.java.net/jdk7/docs/api/java/lang/invoke/MethodHandle.html, "Usage examples"
  void m() throws Throwable {
    Object x, y; String s; int i;
    MethodType mt; MethodHandle mh;
    MethodHandles.Lookup lookup = MethodHandles.lookup();

// mt is (char,char)String
    mt = MethodType.methodType(String.class, char.class, char.class);
    mh = lookup.findVirtual(String.class, "replace", mt);
    s = (String) mh.invokeExact("daddy",'d','n');

// invokeExact(Ljava/lang/String;CC)Ljava/lang/String;
    assert(s.equals("nanny"));

// weakly typed invocation (using MHs.invoke)
    s = (String) mh.invokeWithArguments("sappy", 'p', 'v');
    assert(s.equals("savvy"));

// mt is (Object[])List
    mt = MethodType.methodType(java.util.List.class, Object[].class);
    mh = lookup.findStatic(java.util.Arrays.class, "asList", mt);
    assert(mh.isVarargsCollector());
    x = mh.invokeGeneric("one", "two");

// invokeGeneric(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;
    assert(x.equals(java.util.Arrays.asList("one","two")));

// mt is (Object,Object,Object)Object
    mt = MethodType.genericMethodType(3);
    mh = mh.asType(mt);
    x = mh.invokeExact((Object)1, (Object)2, (Object)3);

// invokeExact(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
    assert(x.equals(java.util.Arrays.asList(1,2,3)));

// mt is int()
    mt = MethodType.methodType(int.class);
    mh = lookup.findVirtual(java.util.List.class, "size", mt);
    i = (int) mh.invokeExact(java.util.Arrays.asList(1,2,3));

// invokeExact(Ljava/util/List;)I
    assert(i == 3);
    mt = MethodType.methodType(void.class, String.class);
    mh = lookup.findVirtual(java.io.PrintStream.class, "println", mt);
    mh.invokeExact(System.out, "Hello, world.");
// invokeExact(Ljava/io/PrintStream;Ljava/lang/String;)V

    MethodHandle mh0 = lookup.findVirtual(String.class, "length", MethodType.methodType(int.class));
    MethodHandle mh1 = MethodHandles.convertArguments(mh0, MethodType.methodType(Integer.class, String.class));
    System.out.println((Integer) mh1.invokeExact("daddy"));
  }
  
  void supported() {
    Object o = 42;
    int i = (int) o;
    String s = "";
    int i1 = <error descr="Inconvertible types; cannot cast 'java.lang.String' to 'int'">(int) s</error>;
    System.out.println(i);
    m((int) o);
  }

  void unsupported() {
    Object o = 42;
    if (<error descr="Inconvertible types; cannot cast 'java.lang.Object' to 'int'">o instanceof int</error>) {
      int i = (Integer) o;
      System.out.println(i);
    }
  }

  void m(int i) { }
  
  void asLongs(Integer i) {
      long l = (long) i;
  }

  void foo(Object o) {}
  public void cast2(Byte operand) {
    foo((<warning descr="Casting 'operand' to 'byte' is redundant">byte</warning>)operand);
    foo((short)operand);
    foo(<error descr="Inconvertible types; cannot cast 'java.lang.Byte' to 'char'">(char)operand</error>);
    foo((int)operand);
    foo((long)operand);
    foo((float)operand);
    foo((double)operand);
    foo(<error descr="Inconvertible types; cannot cast 'java.lang.Byte' to 'boolean'">(boolean)operand</error>);
  }
  public void cast2(Short operand) {
    foo(<error descr="Inconvertible types; cannot cast 'java.lang.Short' to 'byte'">(byte)operand</error>);
    foo((<warning descr="Casting 'operand' to 'short' is redundant">short</warning>)operand);
    foo(<error descr="Inconvertible types; cannot cast 'java.lang.Short' to 'char'">(char)operand</error>);
    foo((int)operand);
    foo((long)operand);
    foo((float)operand);
    foo((double)operand);
    foo(<error descr="Inconvertible types; cannot cast 'java.lang.Short' to 'boolean'">(boolean)operand</error>);
  }
  public void cast2(Character operand) {
    foo(<error descr="Inconvertible types; cannot cast 'java.lang.Character' to 'byte'">(byte)operand</error>);
    foo(<error descr="Inconvertible types; cannot cast 'java.lang.Character' to 'short'">(short)operand</error>);
    foo((<warning descr="Casting 'operand' to 'char' is redundant">char</warning>)operand);
    foo((int)operand);
    foo((long)operand);
    foo((float)operand);
    foo((double)operand);
    foo(<error descr="Inconvertible types; cannot cast 'java.lang.Character' to 'boolean'">(boolean)operand</error>);
  }
  public void cast2(Integer operand) {
    foo(<error descr="Inconvertible types; cannot cast 'java.lang.Integer' to 'byte'">(byte)operand</error>);
    foo(<error descr="Inconvertible types; cannot cast 'java.lang.Integer' to 'short'">(short)operand</error>);
    foo(<error descr="Inconvertible types; cannot cast 'java.lang.Integer' to 'char'">(char)operand</error>);
    foo((<warning descr="Casting 'operand' to 'int' is redundant">int</warning>)operand);
    foo((long)operand);
    foo((float)operand);
    foo((double)operand);
    foo(<error descr="Inconvertible types; cannot cast 'java.lang.Integer' to 'boolean'">(boolean)operand</error>);
  }
  public void cast2(Long operand) {
    foo(<error descr="Inconvertible types; cannot cast 'java.lang.Long' to 'byte'">(byte)operand</error>);
    foo(<error descr="Inconvertible types; cannot cast 'java.lang.Long' to 'short'">(short)operand</error>);
    foo(<error descr="Inconvertible types; cannot cast 'java.lang.Long' to 'char'">(char)operand</error>);
    foo(<error descr="Inconvertible types; cannot cast 'java.lang.Long' to 'int'">(int)operand</error>);
    foo((<warning descr="Casting 'operand' to 'long' is redundant">long</warning>)operand);
    foo((float)operand);
    foo((double)operand);
    foo(<error descr="Inconvertible types; cannot cast 'java.lang.Long' to 'boolean'">(boolean)operand</error>);
  }
  public void cast2(Float operand) {
    foo(<error descr="Inconvertible types; cannot cast 'java.lang.Float' to 'byte'">(byte)operand</error>);
    foo(<error descr="Inconvertible types; cannot cast 'java.lang.Float' to 'short'">(short)operand</error>);
    foo(<error descr="Inconvertible types; cannot cast 'java.lang.Float' to 'char'">(char)operand</error>);
    foo(<error descr="Inconvertible types; cannot cast 'java.lang.Float' to 'int'">(int)operand</error>);
    foo(<error descr="Inconvertible types; cannot cast 'java.lang.Float' to 'long'">(long)operand</error>);
    foo((<warning descr="Casting 'operand' to 'float' is redundant">float</warning>)operand);
    foo((double)operand);
    foo(<error descr="Inconvertible types; cannot cast 'java.lang.Float' to 'boolean'">(boolean)operand</error>);
  }
  public void cast2(Double operand) {
    foo(<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'byte'">(byte)operand</error>);
    foo(<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'short'">(short)operand</error>);
    foo(<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'char'">(char)operand</error>);
    foo(<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'int'">(int)operand</error>);
    foo(<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'long'">(long)operand</error>);
    foo(<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'float'">(float)operand</error>);
    foo((<warning descr="Casting 'operand' to 'double' is redundant">double</warning>)operand);
    foo(<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'boolean'">(boolean)operand</error>);
  }
  public void cast2(Boolean operand) {
    foo(<error descr="Inconvertible types; cannot cast 'java.lang.Boolean' to 'byte'">(byte)operand</error>);
    foo(<error descr="Inconvertible types; cannot cast 'java.lang.Boolean' to 'short'">(short)operand</error>);
    foo(<error descr="Inconvertible types; cannot cast 'java.lang.Boolean' to 'char'">(char)operand</error>);
    foo(<error descr="Inconvertible types; cannot cast 'java.lang.Boolean' to 'int'">(int)operand</error>);
    foo(<error descr="Inconvertible types; cannot cast 'java.lang.Boolean' to 'long'">(long)operand</error>);
    foo(<error descr="Inconvertible types; cannot cast 'java.lang.Boolean' to 'float'">(float)operand</error>);
    foo(<error descr="Inconvertible types; cannot cast 'java.lang.Boolean' to 'double'">(double)operand</error>);
    foo((<warning descr="Casting 'operand' to 'boolean' is redundant">boolean</warning>)operand);
  }
  public void cast2(Object operand) {
    foo((<warning descr="Casting 'operand' to 'byte' is redundant">byte</warning>)operand);
    foo((<warning descr="Casting 'operand' to 'short' is redundant">short</warning>)operand);
    foo((<warning descr="Casting 'operand' to 'char' is redundant">char</warning>)operand);
    foo((<warning descr="Casting 'operand' to 'int' is redundant">int</warning>)operand);
    foo((<warning descr="Casting 'operand' to 'long' is redundant">long</warning>)operand);
    foo((<warning descr="Casting 'operand' to 'float' is redundant">float</warning>)operand);
    foo((<warning descr="Casting 'operand' to 'double' is redundant">double</warning>)operand);
    foo((<warning descr="Casting 'operand' to 'boolean' is redundant">boolean</warning>)operand);
  }
}