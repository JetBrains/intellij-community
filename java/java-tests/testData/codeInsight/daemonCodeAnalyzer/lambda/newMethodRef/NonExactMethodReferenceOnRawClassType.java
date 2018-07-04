import java.util.function.Consumer;

class InlineRef {
  <K> void foo(Consumer<K> f) {}

  void bar(){
    foo(Descriptor::<error descr="Incompatible types: Object is not convertible to Descriptor">getName</error>);
  }
}


class Descriptor<Y> {
  static void getName(Descriptor d) {}
}
