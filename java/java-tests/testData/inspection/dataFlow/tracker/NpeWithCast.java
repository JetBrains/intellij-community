/*
May be null (foo)
  'foo' was assigned (getFoo())
    Method 'getFoo' is annotated as 'nullable' (@Nullable)
 */

import org.jetbrains.annotations.Nullable;

class Test {

  void test(Object x) {
    String foo = (String)getFoo();
    System.out.println(<selection>foo</selection>.trim());
  }

  @Nullable native Object getFoo();
}