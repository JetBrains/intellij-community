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
  interface I {
    void m() default { }
  }

  interface II extends I {
    void m() default { I.super.m(); }
    void ma();
  }

  void test() {
    new I(){}.m();

    new II() {
      public void ma() {
        <error descr="'C.I' is not an enclosing class">I.super</error>.m();
        II.super.m();
        <error descr="Abstract method 'ma()' cannot be accessed directly">II.super.ma()</error>;
      }
    }.m();
  }
}