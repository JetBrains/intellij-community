// "Add on demand static import for 'test.Foo.Bar'" "false"
package test;

import java.util.List;

abstract class Foo implements List<F<caret>oo.Bar> {
  class Bar {}
}