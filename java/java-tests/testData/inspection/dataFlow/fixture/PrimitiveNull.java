import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class A {
  static final <error descr="Incompatible types. Found: 'null', required: 'int'">int x = null;</error>

  void test() {
    long y = x;
  }
}
