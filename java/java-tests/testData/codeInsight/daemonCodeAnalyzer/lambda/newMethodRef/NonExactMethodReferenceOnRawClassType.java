import java.util.function.Consumer;

class InlineRef {
  <K> void foo(Consumer<K> f) {}

  void bar(){
    foo(Descriptor::<error descr="Cannot resolve method 'getName'">getName</error>);
  }
}


class Descriptor<Y> {
  static void getName(Descriptor d) {}
}
