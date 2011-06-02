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

  void unsupported() {
    Object o = 42;
    int i = <error descr="Inconvertible types; cannot cast 'java.lang.Object' to 'int'">(int) o</error>;
    System.out.println(i);
    m(<error descr="Inconvertible types; cannot cast 'java.lang.Object' to 'int'">(int) o</error>);
    if (<error descr="Inconvertible types; cannot cast 'java.lang.Object' to 'int'">o instanceof int</error>) {
      i = (Integer) o;
      System.out.println(i);
    }
  }

  void m(int i) { }
}