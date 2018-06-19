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

import java.io.*;

class Const {
  public static final int X = 1;
  public static final int Y = 2;
  public static final int Z = 4;
}
public class X {

  void f(@MagicConstant(intValues={Const.X, Const.Y, Const.Z}) int x) {
    /////////// BAD
    f(<warning descr="Should be one of: Const.X, Const.Y, Const.Z">0</warning>);
    f(<warning descr="Should be one of: Const.X, Const.Y, Const.Z">1</warning>);
    f(<warning descr="Should be one of: Const.X, Const.Y, Const.Z">Const.X | Const.Y</warning>);
    int i = Const.X | Const.Y;
    f(<warning descr="Should be one of: Const.X, Const.Y, Const.Z">i</warning>);
    if (x == <warning descr="Should be one of: Const.X, Const.Y, Const.Z">3</warning>) {
      x = <warning descr="Should be one of: Const.X, Const.Y, Const.Z">2</warning>;
      assert x != <warning descr="Should be one of: Const.X, Const.Y, Const.Z">1</warning>;
    }

    ////////////// GOOD
    f(Const.X);
    f(Const.Y);
    f(Const.Z);
    int i2 = this == null ? Const.X : Const.Y;
    f(i2);
    if (x == Const.X) {
      x = Const.Y;
      assert x != Const.Z;
    }

    f2(x);
  }

  void f2(@MagicConstant(valuesFromClass =Const.class) int x) {
    /////////// BAD
    f2(<warning descr="Should be one of: Const.X, Const.Y, Const.Z">0</warning>);
    f2(<warning descr="Should be one of: Const.X, Const.Y, Const.Z">1</warning>);
    f2(<warning descr="Should be one of: Const.X, Const.Y, Const.Z">Const.X | Const.Y</warning>);
    int i = Const.X | Const.Y;
    f2(<warning descr="Should be one of: Const.X, Const.Y, Const.Z">i</warning>);
    if (x == <warning descr="Should be one of: Const.X, Const.Y, Const.Z">3</warning>) {
      x = <warning descr="Should be one of: Const.X, Const.Y, Const.Z">2</warning>;
      assert x != <warning descr="Should be one of: Const.X, Const.Y, Const.Z">1</warning>;
    }

    ////////////// GOOD
    f2(Const.X);
    f2(Const.Y);
    f2(Const.Z);
    int i2 = this == null ? Const.X : Const.Y;
    f2(i2);
    if (x == Const.X) {
      x = Const.Y;
      assert x != Const.Z;
    }

    f(x);
  }

  void f3(@MagicConstant(flags ={Const.X, Const.Y, Const.Z}) int x) {
    /////////// BAD
    f3(<warning descr="Should be one of: Const.X, Const.Y, Const.Z or their combination">2</warning>);
    f3(<warning descr="Should be one of: Const.X, Const.Y, Const.Z or their combination">1</warning>);
    f(<warning descr="Should be one of: Const.X, Const.Y, Const.Z">Const.X | Const.Y</warning>);
    int i = Const.X | 4;
    f3(<warning descr="Should be one of: Const.X, Const.Y, Const.Z or their combination">i</warning>);
    if (x == <warning descr="Should be one of: Const.X, Const.Y, Const.Z or their combination">3</warning>) {
      x = <warning descr="Should be one of: Const.X, Const.Y, Const.Z or their combination">2</warning>;
      assert x != <warning descr="Should be one of: Const.X, Const.Y, Const.Z or their combination">1</warning>;
    }

    ////////////// GOOD
    f3(Const.X);
    f3(Const.Y);
    f3(Const.Z);

    int i2 = this == null ? Const.X : Const.Y;
    f3(i2);
    int ix = Const.X | Const.Y;
    f3(ix);
    f3(0);
    f3(-1);
    int f = 0;
    if (x == Const.X) {
      x = Const.Y;
      assert x != Const.Z;
      f |= Const.Y;  f &= Const.X & ~(Const.Z | Const.X);
    }
    else {
      f |= Const.X;  f = f & ~(Const.X | Const.X);
    }
    f3(f);

    f4(x);
  }

  void f4(@MagicConstant(flagsFromClass =Const.class) int x) {
    /////////// BAD
    f4(<warning descr="Should be one of: Const.X, Const.Y, Const.Z or their combination">-3</warning>);
    f4(<warning descr="Should be one of: Const.X, Const.Y, Const.Z or their combination">1</warning>);
    f4(Const.X | Const.Y);
    int i = Const.X | 4;
    f4(<warning descr="Should be one of: Const.X, Const.Y, Const.Z or their combination">i</warning>);
    if (x == <warning descr="Should be one of: Const.X, Const.Y, Const.Z or their combination">3</warning>) {
      x = <warning descr="Should be one of: Const.X, Const.Y, Const.Z or their combination">2</warning>;
      assert x != <warning descr="Should be one of: Const.X, Const.Y, Const.Z or their combination">1</warning>;
    }

    ////////////// GOOD
    f4(Const.X);
    f4(Const.Y);
    f4(Const.Z);

    int i2 = this == null ? Const.X : Const.Y;
    f4(i2);
    int ix = Const.X | Const.Y;
    f4(ix);
    f4(0);
    f4(-1);
    int f = 0;
    if (x == Const.X) {
      x = Const.Y;
      assert x != Const.Z;
      f |= Const.Y;
    }
    else {
      f |= Const.X;
    }
    f4(f);

    f3(x);
  }

  @MagicConstant(intValues={Const.X, Const.Y, Const.Z})
  @interface IntEnum{}

  class Alias {

    void f(@IntEnum int x) {
      ////////////// GOOD
      f(Const.X);
      f(Const.Y);
      f(Const.Z);
      int i2 = this == null ? Const.X : Const.Y;
      f(i2);
      if (x == Const.X) {
        x = Const.Y;
        assert x != Const.Z;
      }

      f2(x);

      /////////// BAD
      f(<warning descr="Should be one of: Const.X, Const.Y, Const.Z">0</warning>);
      f(<warning descr="Should be one of: Const.X, Const.Y, Const.Z">1</warning>);
      f(<warning descr="Should be one of: Const.X, Const.Y, Const.Z">Const.X | Const.Y</warning>);
      int i = Const.X | Const.Y;
      f(<warning descr="Should be one of: Const.X, Const.Y, Const.Z">i</warning>);
      if (x == <warning descr="Should be one of: Const.X, Const.Y, Const.Z">3</warning> || getClass().isInterface()) {
        x = <warning descr="Should be one of: Const.X, Const.Y, Const.Z">2</warning>;
        assert x != <warning descr="Should be one of: Const.X, Const.Y, Const.Z">1</warning>;
      }

      f2(x);
    }
  }

  @interface III {
    @MagicConstant(intValues = {Const.X, Const.Y}) int val();
  }
  class MagicAnnoInsideAnnotationUsage {

    // bad
    @III(val = <warning descr="Should be one of: Const.X, Const.Y">2</warning>)
    int h;
    @III(val = <warning descr="Should be one of: Const.X, Const.Y">Const.X | Const.Y</warning>)
    void f(){}

    // good
    @III(val = Const.X)
    int h2;
  }

  abstract class BeanInfoParsing {
    /**
     * @see        java.lang.Runtime#exit(int)
     *
     * @beaninfo
     *   preferred: true
     *       bound: true
     *        enum: DO_NOTHING_ON_CLOSE Const.X
     *              HIDE_ON_CLOSE       Const.Y
     * description: The frame's default close operation.
     */
    public void setX(int operation) {

    }

    public abstract int getX();

    {
      // good
      setX(Const.X);
      setX(Const.Y);
      if (getX() == Const.X || getX() == Const.Y) {}

      // bad
      setX(<warning descr="Should be one of: Const.X, Const.Y">0</warning>);
      setX(<warning descr="Should be one of: Const.X, Const.Y">-1</warning>);
      setX(<warning descr="Should be one of: Const.X, Const.Y">Const.Z</warning>);
      if (getX() == <warning descr="Should be one of: Const.X, Const.Y">1</warning>) {}
      if (getX() == <warning descr="Should be one of: Const.X, Const.Y">Const.Z</warning>) {}
    }

  }

  class ExternalAnnotations {
    void f() {
      java.util.Calendar.getInstance().set(2000,<warning descr="Should be one of: Calendar.JANUARY, Calendar.FEBRUARY, Calendar.MARCH, Calendar.APRIL, Calendar.MAY, Calendar.JUNE, Calendar.JULY, Calendar.AUGUST, Calendar.SEPTEMBER, Calendar.OCTOBER, Calendar.NOVEMBER, Calendar.DECEMBER">9</warning>,0)<EOLError descr="';' expected"></EOLError>
      new javax.swing.JLabel("text", <warning descr="Should be one of: SwingConstants.LEFT, SwingConstants.CENTER, SwingConstants.RIGHT, SwingConstants.LEADING, SwingConstants.TRAILING">3</warning>);
    }
  }
  static class OverrideX extends X {
    void f(int x) {
      super.f(x);
    }
  }

  void plusSupportedInFlags(@MagicConstant(flags ={Const.X, Const.Y, Const.Z}) int x) {
    ////////////// GOOD
    plusSupportedInFlags(Const.X + Const.Y);
    plusSupportedInFlags(Const.Z + Const.X + Const.Y);
    plusSupportedInFlags(Const.Z + (Const.X + Const.Y));

    int ix = Const.X + Const.Y;
    plusSupportedInFlags(ix);
    plusSupportedInFlags(0);
    plusSupportedInFlags(-1);
  }

  ///////////////////////////////////////
  static class FontType {
    public static final int PLAIN = 0;
    public static final int BOLD = 1;
    public static final int ITALIC = 2;
  }
  void font(@MagicConstant(flags = {FontType.PLAIN, FontType.BOLD, FontType.ITALIC}) int x) {
    // 0 is not allowed despite the fact that it's flags parameter
    font(<warning descr="Should be one of: FontType.PLAIN, FontType.BOLD, FontType.ITALIC or their combination">0</warning>);
  }
}
