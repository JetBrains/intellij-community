// "Add on demand static import for 'test.Foo'" "true"
package test;

import java.util.List;

import static test.Foo.*;

abstract class Foo implements List<Bar> {
  static class Bar {}
}