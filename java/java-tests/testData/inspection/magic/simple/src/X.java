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
  public static final int X = 0;
  public static final int Y = 2;
  public static final int Z = 3;
}
public class X {

  void f(@MagicConstant(intValues={Const.X, Const.Y, Const.Z}) int x) {
    /////////// BAD
    f(0);
    f(1);
    f(Const.X | Const.Y);
    int i = Const.X | Const.Y;
    f(i);
    if (x == 3) {
      x = 2;
      assert x != 1;
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
    f2(0);
    f2(1);
    f2(Const.X | Const.Y);
    int i = Const.X | Const.Y;
    f2(i);
    if (x == 3) {
      x = 2;
      assert x != 1;
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
    f3(2);
    f3(1);
    f(Const.X | Const.Y);
    int i = Const.X | 4;
    f3(i);
    if (x == 3) {
      x = 2;
      assert x != 1;
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
    f4(-3);
    f4(1);
    f4(Const.X | Const.Y);
    int i = Const.X | 4;
    f4(i);
    if (x == 3) {
      x = 2;
      assert x != 1;
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


  class Alias {
    @MagicConstant(intValues={Const.X, Const.Y, Const.Z})
    @interface IntEnum{}

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
      f(0);
      f(1);
      f(Const.X | Const.Y);
      int i = Const.X | Const.Y;
      f(i);
      if (x == 3 || getClass().isInterface()) {
        x = 2;
        assert x != 1;
      }

      f2(x);
    }
  }

  class MagicAnnoInsideAnnotationUsage {
    @interface III {
      @MagicConstant(intValues = {Const.X, Const.Y}) int val();
    }

    // bad
    @III(val = 2)
    int h;
    @III(val = Const.X | Const.Y)
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
      setX(0);
      setX(-1);
      setX(Const.Z);
      if (getX() == 1) {}
      if (getX() == Const.Z) {}
    }

  }

  class ExternalAnnotations {
    void f() {
      java.util.Calendar.getInstance().set(2000,9,0)
      new javax.swing.JLabel("text", 3);
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
}
