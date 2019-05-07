/*
Value is always false (b4; line#18)
  'b4' was assigned (=; line#17)
    'b3' was assigned (=; line#16)
      'b2' was assigned (=; line#15)
        'b1' was assigned (=; line#14)
          'b == false' was established from condition (b; line#13)
 */
import org.jetbrains.annotations.NotNull;

class Test {
  void test(boolean b) {
    if (b) return;
    boolean b1 = b;
    boolean b2 = b1;
    boolean b3 = b2;
    boolean b4 = b3;
    if (<selection>b4</selection>) {}
  }
}