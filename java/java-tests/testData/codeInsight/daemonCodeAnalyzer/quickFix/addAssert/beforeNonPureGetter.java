// "Introduce variable and assert 'string != null'" "true-preview"
import org.jetbrains.annotations.Nullable;

class A {
  // tracked by the dataflow analysis as a getter, but not pure, so it must not be called twice
  native @Nullable String getString();

  void test() {
    System.out.println(getString().tri<caret>m());
  }
}
