// "Introduce variable and assert 'string != null'" "true-preview"
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

class A {
  @Contract(pure = true)
  native @Nullable String getString(int i);

  int test(int i) {
      String string = getString(i);
      assert string != null;
      return string.length();
  }
}
