package templates;

import java.util.Arrays;

public class Foo {
  void m(int[] array) {
      Arrays.stream(array)<caret>
  }
}