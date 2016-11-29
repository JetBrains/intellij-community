interface I {
  Object fun(Object o);
}

interface MyStream {
  MyStream map(I i);
}

class Test {

  class StrType {
    static MyStream of(int b) {}
  }

  {
    StrType Stream = null;
    new Runnable() {
      void run() {
        Stream.of(1).map(p -> "b"));
      }
    }

  }
}
