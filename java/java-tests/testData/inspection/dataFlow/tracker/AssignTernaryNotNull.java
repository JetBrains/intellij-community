/*
Value is always false (foo == null; line#15)
  'foo' was assigned (=; line#14)
    One of the following happens:
      Expression cannot be null as it's newly created object (new String("bar"); line#14)
      or expression cannot be null as it's newly created object (new String("foo"); line#14)
 */

import org.jetbrains.annotations.Nullable;

class Test {

  void test(boolean b) {
    String foo = b ? new String("foo") : new String("bar");
    if (<selection>foo == null</selection>) {
      
    }
  }
}