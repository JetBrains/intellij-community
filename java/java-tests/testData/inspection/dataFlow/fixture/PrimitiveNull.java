import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class A {
  static final int x = <error descr="Incompatible types. Found: 'null', required: 'int'">null;</error>

  void test() {
    long y = x;
  }
}
