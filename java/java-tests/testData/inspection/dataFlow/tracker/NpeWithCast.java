/*
May be null (foo; line#13)
  'foo' was assigned (=; line#12)
    Method 'getFoo' is annotated as 'nullable' (@Nullable; line#16)
 */

import org.jetbrains.annotations.Nullable;

class Test {

  void test(Object x) {
    String foo = (String)getFoo();
    System.out.println(<selection>foo</selection>.trim());
  }

  @Nullable native Object getFoo();
}