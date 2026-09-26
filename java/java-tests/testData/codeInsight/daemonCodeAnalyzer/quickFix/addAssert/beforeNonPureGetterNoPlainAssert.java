// "Assert 'getString() != null'" "false"
import org.jetbrains.annotations.Nullable;

class A {
  // tracked by the dataflow analysis as a getter, but not pure, so asserting it in-place would call it twice
  native @Nullable String getString();

  void test() {
    System.out.println(getString().tri<caret>m());
  }
}
