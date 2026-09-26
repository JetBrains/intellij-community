// "Introduce variable and assert 'string != null'" "true-preview"
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

class A {
  @Contract(pure = true)
  native @Nullable String getString(int i);

  void test(int i) {
    if (getString(i).isEmp<caret>ty()) System.out.println();
  }
}
