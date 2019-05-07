/*
Value is always false (foo > 3; line#16)
  One of the following happens:
    Left operand is 1 (foo; line#16)
      'foo' was assigned (=; line#15)
    or left operand is 2 (foo; line#16)
      'foo' was assigned (=; line#15)
 */

import org.jetbrains.annotations.Nullable;

class Test {

  void test(boolean b) {
    int foo = b ? 1 : 2;
    if (<selection>foo > 3</selection>) {
      
    }
  }
}