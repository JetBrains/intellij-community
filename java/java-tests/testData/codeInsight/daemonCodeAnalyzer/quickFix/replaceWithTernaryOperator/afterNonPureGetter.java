// "Introduce variable and replace with 'string != null ?:'" "true-preview"
import org.jetbrains.annotations.Nullable;

class A {
  // tracked by the dataflow analysis as a getter, but not pure, so it must not be called twice
  native @Nullable String getString();

  int test() {
      String string = getString();
      return string != null ? string.length() : 0;
  }
}
