// "Introduce variable and assert 'string != null'" "true-preview"
import org.jetbrains.annotations.Nullable;

class A {
  native @Nullable String getString(int i);

  void test(int i) {
      String string = getString(i);
      assert string != null;
      System.out.println(string.trim());
  }
}
