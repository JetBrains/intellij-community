package p;

import java.io.IOException;

class Foo {

  {
    try (MyReader c1 = new MyReader() {}) {

    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }

}