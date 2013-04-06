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
class C {
  static class E extends Exception { }
  static class E1 extends E { }
  static class E2 extends E { }
  static class E3 extends E { }

  static class MyResource implements AutoCloseable {
    public MyResource() throws E1 { }
    public void doSomething() throws E2 { }
    @Override public void close() throws E3 { }
  }

  static interface I extends AutoCloseable { }

  void m1() {
    try (MyResource r = new MyResource()) { r.doSomething(); }
    catch (E1 | E2 | E3 ignore) { }

    try (MyResource r = new MyResource()) { }
    catch (E1 | E3 ignore) { }

    try (<error descr="Unhandled exception from auto-closeable resource: C.E3">MyResource r = new MyResource()</error>) { }
    catch (E1 e) { }

    try (MyResource r = <error descr="Unhandled exception: C.E1">new MyResource()</error>) { }
    catch (E3 e) { }

    try (MyResource r = <error descr="Unhandled exception: C.E1">new MyResource()</error>) { }

    try (<error descr="Unhandled exception from auto-closeable resource: java.lang.Exception">I r = null</error>) { System.out.println(r); }
  }

  void m2() throws Exception {
    try (<error descr="Incompatible types. Found: 'java.lang.Object', required: 'java.lang.AutoCloseable'">Object r = new MyResource()</error>) { }

    try (<error descr="Incompatible types. Found: 'java.lang.String', required: 'java.lang.AutoCloseable'">AutoCloseable r = "resource"</error>) { }
  }

  void m3(int p) throws Exception {
    try (MyResource r = new MyResource()) {
      r.doSomething();
      <error descr="Cannot assign a value to final variable 'r'">r</error> = null;
      int <error descr="Variable 'r' is already defined in the scope">r</error> = 0;
    }
    catch (E e) {
      <error descr="Cannot resolve symbol 'r'">r</error> = null;
    }
    finally {
      <error descr="Cannot resolve symbol 'r'">r</error> = null;
    }
    <error descr="Cannot resolve symbol 'r'">r</error> = null;

    try (MyResource r = new MyResource(); MyResource <error descr="Variable 'r' is already defined in the scope">r</error> = new MyResource()) { }

    try (MyResource r1 = new MyResource(); MyResource r2 = r1) { }

    try (MyResource r1 = <error descr="Cannot resolve symbol 'r2'">r2</error>; MyResource r2 = r1) { }

    MyResource r = null;
    try (MyResource <error descr="Variable 'r' is already defined in the scope">r</error> = new MyResource()) { }
    try (MyResource rr = r) { }

    try (MyResource <error descr="Variable 'p' is already defined in the scope">p</error> = new MyResource()) { }
    new Runnable() {
      public void run() {
        try (MyResource p = new MyResource()) { }
        catch (E e) { }
      }
    }.run();
  }

  void m4() throws Exception {
    try (MyResource r = <error descr="Variable 'r' might not have been initialized">r</error>) { }

    MyResource r;
    try (MyResource r1 = <error descr="Variable 'r' might not have been initialized">r</error>) { }
  }
}