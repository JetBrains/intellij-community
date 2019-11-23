import java.util.function.*;

class X{
  int foo;

  void test() {
    IntSupplier r = () -> <selection>foo</selection>;
  }
}
