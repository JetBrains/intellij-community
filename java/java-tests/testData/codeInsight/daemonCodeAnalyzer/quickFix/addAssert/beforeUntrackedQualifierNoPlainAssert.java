// "Assert 'getString(i) != null'" "false"
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

class A {
  @Contract(pure = true)
  native @Nullable String getString(int i);

  void test(int i) {
    System.out.println(getString(i).tri<caret>m());
  }
}
