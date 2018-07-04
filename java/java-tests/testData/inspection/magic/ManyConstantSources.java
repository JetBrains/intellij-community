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

  void f(@MagicConstant(intValues = {Const1.X, Const2.I}) int x) {
    /////////// BAD
    f(<warning descr="Should be one of: Const1.X, Const2.I">0</warning>);
    f(<warning descr="Should be one of: Const1.X, Const2.I">1</warning>);
    f(<warning descr="Should be one of: Const1.X, Const2.I">Const1.X | Const2.I</warning>);

    ////////////// GOOD
    f(Const1.X);
    f(Const2.I);

    f2(x);
  }

  void f2(@MagicConstant(valuesFromClass = Const1.class, intValues = {Const2.I}) int x) {
    /////////// BAD
    f2(<warning descr="Should be one of: Const2.I, Const1.X">0</warning>);
    f2(<warning descr="Should be one of: Const2.I, Const1.X">1</warning>);
    f2(<warning descr="Should be one of: Const2.I, Const1.X">Const1.X | Const2.I</warning>);

    ////////////// GOOD
    f2(Const1.X);
    f2(Const2.I);

    f(x);
  }

  void f3(@MagicConstant(flags = {Const1.X, Const2.I}) int x) {
    /////////// BAD
    f3(<warning descr="Should be one of: Const1.X, Const2.I or their combination">2</warning>);
    f3(<warning descr="Should be one of: Const1.X, Const2.I or their combination">1</warning>);
    f(<warning descr="Should be one of: Const1.X, Const2.I">Const1.X | Const2.I</warning>);
    int i = Const1.X | 4;
    f3(<warning descr="Should be one of: Const1.X, Const2.I or their combination">i</warning>);

    ////////////// GOOD
    f3(Const1.X);
    f3(Const2.I);

    f4(x);
  }

  void f4(@MagicConstant(flagsFromClass = Const1.class, flags = {Const2.I}) int x) {
    /////////// BAD
    f4(<warning descr="Should be one of: Const2.I, Const1.X or their combination">-3</warning>);
    f4(<warning descr="Should be one of: Const2.I, Const1.X or their combination">1</warning>);
    
    ////////////// GOOD
    f4(Const1.X);
    f4(Const2.I);
    f4(Const1.X | Const2.I);

    f3(x);
  }
}
