
import java.util.function.Supplier;
class Foo {

  static int m() {
    return 1;
  }

  void o(){
    mm(Foo::<caret>m);
  }

  void mm(Supplier<Integer> r) {}
}