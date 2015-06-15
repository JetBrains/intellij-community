package templates;

import java.lang.Exception;

public class Foo {
  void m(boolean b, int value) {
      throw new Exception("".format<caret>
  }
}