import java.util.function.*;

class X{
  int foo;

  void test() {
    IntSupplier r = () -> {
        int foo = this.foo;
        return foo;
    };
  }
}
