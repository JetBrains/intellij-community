// "Assert 'getString() != null'" "true-preview"
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

class A {
  @Contract(pure = true)
  native @Nullable String getString();

  void test() {
    System.out.println(getString().tri<caret>m());
  }
}
