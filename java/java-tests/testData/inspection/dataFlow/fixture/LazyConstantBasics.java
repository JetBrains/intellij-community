import org.jetbrains.annotations.NotNull;

public class LazyConstantBasics {
  static final @NotNull LazyConstant<String> VALUE = LazyConstant.of(() -> "value");
  void test() {
    String s = VALUE.get();
    if (<warning descr="Condition 's.equals(VALUE.get())' is always 'true'">s.equals(VALUE.get())</warning>) {

    }
  }
}