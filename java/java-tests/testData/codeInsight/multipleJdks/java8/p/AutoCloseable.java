package p;

import ppp.*;

class Foo {
  void foo(Bar bar) {
    final ByteArrayOStream baos = bar.alloc();
  }

  class Bar {
    ByteArrayOStream alloc() {
      return null;
    }
  }
}