import java.util.function.Consumer;

class InlineRef {
  <K> void foo(Consumer<K> f) {}

  void bar(){
    foo(Descriptor::<error descr="Invalid method reference: Object cannot be converted to Descriptor">getName</error>);
  }
}


class Descriptor<Y> {
  static void getName(Descriptor d) {}
}
