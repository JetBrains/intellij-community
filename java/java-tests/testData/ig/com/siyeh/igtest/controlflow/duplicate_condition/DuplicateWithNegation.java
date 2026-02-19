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
package com.siyeh.igtest.controlflow.duplicate_condition;

public class DuplicateWithNegation {
  void test(String string) {
    if (<warning descr="Duplicate condition 'string.startsWith(\"xyz\")'">string.startsWith("xyz")</warning>) {}
    else if (!<warning descr="Duplicate condition 'string.startsWith(\"xyz\")'">string.startsWith("xyz")</warning>) {}
  }

  void test2(String s) {
    if (!(s.startsWith("x") || s.endsWith("y"))) {
    } else if (s.startsWith("x")) {

    }
  }
}