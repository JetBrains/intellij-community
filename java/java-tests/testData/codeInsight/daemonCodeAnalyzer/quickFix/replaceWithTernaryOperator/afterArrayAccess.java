// "Introduce variable and replace with 'array != null ?:'" "true-preview"
import org.jetbrains.annotations.Nullable;

class A {
  // tracked by the dataflow analysis as a getter, but not pure, so it must not be called twice
  native String @Nullable [] getArray();

  int test(int i) {
      String[] array = getArray();
      return array != null ? array[i].length() : 0;
  }
}
