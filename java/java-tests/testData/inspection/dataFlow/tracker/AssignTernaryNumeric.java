/*
Value is always false (foo > 3)
  One of the following happens:
    Left operand is 1 (foo)
      'foo' was assigned (=)
    or left operand is 2 (foo)
      'foo' was assigned (=)
 */

import org.jetbrains.annotations.Nullable;

class Test {

  void test(boolean b) {
    int foo = b ? 1 : 2;
    if (<selection>foo > 3</selection>) {
      
    }
  }
}