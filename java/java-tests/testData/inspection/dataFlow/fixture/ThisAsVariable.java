import java.util.*;
import org.jetbrains.annotations.*;

class ThisAsVariable {
  void instanceOf() {
    if (this instanceof Iterable) {
      return;
    }
    if (<warning descr="Condition 'this instanceof Collection' is always 'false'">this instanceof Collection</warning>) {
      System.out.println("Impossible");
    }
  }

  static class FieldsLocality {
    int a = 10;
    int b = getFoo(); // static: cannot write to 'a', 'a' is not flushed
    int c = <warning descr="Condition 'a > 5' is always 'true'">a > 5</warning> ? 6 : 7;
    int d = getBar(); // may write to 'a'
    int e = a > 5 ? 6 : 7;

    native static int getFoo();
    native int getBar();
  }

  static class FieldsLeakThroughLambda {
    int x = 5;

    FieldsLeakThroughLambda() {
      if (<warning descr="Condition 'x > 5' is always 'false'">x > 5</warning>) {
        System.out.println("Impossible");
      }
      Runnable r = () -> System.out.println("Hello");
      r.run();
      if (<warning descr="Condition 'x > 5' is always 'false'">x > 5</warning>) {
        System.out.println("Impossible");
      }
      Runnable r2 = () -> changeX();
      if (<warning descr="Condition 'x > 5' is always 'false'">x > 5</warning>) {
        System.out.println("Impossible");
      }
      r2.run();
      if (x > 5) {
        System.out.println("Possible");
      }
    }

    void changeX() {
      x = 6;
    }


  }

  static class LocalityPropagation {
    final int[] data = {5,6,7};

    LocalityPropagation() {
      if(<warning descr="Condition 'data[0] < 0' is always 'false'">data[0] < 0</warning>) {
        System.out.println("Impossible");
      }
      if(<warning descr="Condition 'data.length > 3' is always 'false'">data.length > 3</warning>) {
        System.out.println("Impossible");
      }
      FieldsLocality.getFoo(); // cannot update 'data' as it's not leaking
      new FieldsLocality().getBar(); // cannot update 'data' as it's not leaking
      if(<warning descr="Condition 'data[0] < 0' is always 'false'">data[0] < 0</warning>) {
        System.out.println("Impossible");
      }
      doSmth(); // may update 'data', but cannot replace it with another array as it's final
      if(data[0] < 0) {
        System.out.println("Possible");
      }
      if(<warning descr="Condition 'data.length > 3' is always 'false'">data.length > 3</warning>) {
        System.out.println("Still impossible");
      }
    }

    native void doSmth();
  }

  static class PassNullable {
    @Nullable final String s;

    PassNullable(@Nullable String _s) {
      s = _s;
      if (s != null) {
        Runnable r = new Runnable() {
          public void run() {
            System.out.println(s.trim());
          }
        };
        r.run();
      } else {
        Runnable r = new Runnable() {
          public void run() {
            System.out.println(s.<warning descr="Method invocation 'trim' may produce 'java.lang.NullPointerException'">trim</warning>());
          }
        };
        r.run();
      }
    }
  }
}
