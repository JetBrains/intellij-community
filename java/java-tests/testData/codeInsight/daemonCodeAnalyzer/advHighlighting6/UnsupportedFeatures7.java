/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import java.io.*;
import java.util.*;

import static java.lang.Math.*;

@interface Anno { }

@Anno
class UnsupportedFeatures {
  void m(String... args) throws Exception {
    for (String s : args) { System.out.println(s); }

    List<String> list =
      new ArrayList<>();

    for (String s : list) {}
    Arrays.asList("");
    Boolean b = true;
    boolean b1 = Boolean.TRUE;

    try { Reader r = new FileReader("/dev/null"); }
    catch (<error descr="Multi-catches are not supported at this language level">FileNotFoundException | IOException e</error>) { e.printStackTrace(); }

    try <error descr="Try-with-resources are not supported at this language level">(Reader r = new FileReader("/dev/null"))</error> { }

    I i1 = <error descr="Method references are not supported at this language level">UnsupportedFeatures::m</error>;
    I i2 = <error descr="Lambda expressions are not supported at this language level">() -> { }</error>;
  }

  interface I {
    <error descr="Extension methods are not supported at this language level">default void m() { }</error>
  }
}
