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
package pck;

import static pck.Assert.assertEquals;

class Assert {
  static void assertEquals(Object o1, Object o2) {}

  static void assertEquals(long l1, long l2) {}
}

class Test {
  void test() {
    assertEquals<error descr="Ambiguous method call: both 'Assert.assertEquals(Object, Object)' and 'Assert.assertEquals(long, long)' match">(100L, Long.valueOf(100L))</error>;
  }
}