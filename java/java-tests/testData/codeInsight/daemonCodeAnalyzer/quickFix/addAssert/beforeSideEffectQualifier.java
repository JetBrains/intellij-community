// "Introduce variable and assert 'string != null'" "true-preview"
import org.jetbrains.annotations.Nullable;

class A {
  native @Nullable String getString(int i);

  void test(int i) {
    System.out.println(getString(i).tri<caret>m());
  }
}
