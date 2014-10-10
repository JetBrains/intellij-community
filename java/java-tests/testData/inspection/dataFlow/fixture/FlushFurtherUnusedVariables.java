/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import org.jetbrains.annotations.Contract;

class Foo {

  void foo() {
    int a1 = random() ? 1 : 0;
    int a2 = random() ? 1 : 0;
    int a3 = random() ? 1 : 0;
    int a4 = random() ? 1 : 0;
    int a5 = random() ? 1 : 0;
    int a6 = random() ? 1 : 0;
    int a7 = random() ? 1 : 0;
    int a8 = random() ? 1 : 0;
    int a9 = random() ? 1 : 0;
    int a10 = random() ? 1 : 0;
    int a11 = random() ? 1 : 0;
    int a12 = random() ? 1 : 0;
    int a13 = random() ? 1 : 0;
    int a14 = random() ? 1 : 0;
    int a15 = random() ? 1 : 0;
  }

  native boolean random();

}