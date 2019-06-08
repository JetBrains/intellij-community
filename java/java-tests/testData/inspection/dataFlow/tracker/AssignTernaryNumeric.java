/*
Value is always false (foo > 3; line#13)
  Left operand is 1 or 2 (foo; line#13)
    'foo' was assigned (=; line#12)
 */

import org.jetbrains.annotations.Nullable;

class Test {

  void test(boolean b) {
    int foo = b ? 1 : 2;
    if (<selection>foo > 3</selection>) {
      
    }
  }
}