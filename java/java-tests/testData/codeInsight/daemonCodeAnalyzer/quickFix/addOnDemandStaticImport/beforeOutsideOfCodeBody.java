// "Add on demand static import for 'test.Foo'" "true"
package test;

import java.util.List;

abstract class Foo implements List<F<caret>oo.Bar> {
  static class Bar {}
}