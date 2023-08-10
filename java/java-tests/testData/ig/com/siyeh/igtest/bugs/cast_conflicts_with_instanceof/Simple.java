/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
interface I {}
class A implements I {}
class B implements I {}

class U {
  void foo(Object o, boolean b) {
    if (o instanceof I && b) {

    } else {
      if (o instanceof A) {
        B bb = <warning descr="Cast to 'B' type conflicts with preceding 'instanceof A' check">(B)(o)</warning>;
      } else {
      }
    }
  }
}
