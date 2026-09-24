// "Introduce variable and assert 'string != null'" "true-preview"
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

class A {
  @Contract(pure = true)
  native @Nullable String getString(int i);

  int test(int i) {
    return getString(i).len<caret>gth();
  }
}
