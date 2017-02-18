import java.util.function.IntSupplier;
import java.util.Collections;

class Foo {

  void m() {

    Runnable r = Collections::emptyList;

    Runnable r2 = () -> {


      IntSupplier s = () -> 1;

      Runnable r3 = () -> {

      };

    };

    IntSupplier s2 = () -> 1;
  }

  void m2() {

    Runnable r = () -> {

      Runnable r3 = () -> {

        IntSupplier s2 = () -> 1;

        Runnable r4 = () -> {

        };

      };

      IntSupplier s = () -> 1;

    };
  }
}