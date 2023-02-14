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

import org.intellij.lang.annotations.MagicConstant;

class Const1 {
  public static final int X = 1;
}

class Const2 {
  public static final int I = 4;
}

class X {
  @MagicConstant(valuesFromClass = Const1.class, intValues = {Const2.I})
  int foo() {
    return Math.random() > 0.5 ? Const1.X : Const2.I;
  }

  void f(@MagicConstant(intValues = {Const1.X, Const2.I}) int x) {
    /////////// BAD
    switch (x) {
      case <warning descr="Should be one of: Const1.X, Const2.I">0</warning>:
        break;
      case <warning descr="Should be one of: Const1.X, Const2.I">1</warning>:
        break;
      case <warning descr="Should be one of: Const1.X, Const2.I">Const1.X | Const2.I</warning>:
        break;
    }

    switch (foo()) {
      case <warning descr="Should be one of: Const2.I, Const1.X">0</warning>:
        break;
      case <warning descr="Should be one of: Const2.I, Const1.X">1</warning>:
        break;
      case <warning descr="Should be one of: Const2.I, Const1.X">Const1.X | Const2.I</warning>:
        break;
    }

    /////////// GOOD
    switch (x) {
      case Const1.X:
        break;
      case Const2.I:
        break;
    }
  }
}
