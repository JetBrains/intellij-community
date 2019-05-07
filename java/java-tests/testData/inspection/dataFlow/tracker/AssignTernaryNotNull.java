/*
Value is always false (foo == null)
  'foo' was assigned (=)
    One of the following happens:
      Expression cannot be null as it's newly created object (new String("foo"))
      or expression cannot be null as it's newly created object (new String("bar"))
 */

import org.jetbrains.annotations.Nullable;

class Test {

  void test(boolean b) {
    String foo = b ? new String("foo") : new String("bar");
    if (<selection>foo == null</selection>) {
      
    }
  }
}