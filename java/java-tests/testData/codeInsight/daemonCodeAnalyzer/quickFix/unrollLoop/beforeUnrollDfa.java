// "Unroll loop" "true"
import java.util.Arrays;

class X {
  void testLoop(int size) {
    if (size == 5) {
      <caret>for (int i = 0; i < size; i++) {
        System.out.println(i);
      }
    }
  }
}