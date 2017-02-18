interface I {
  Object fun(Object o);
}

interface MyStream {
  MyStream map(I i);
}

class StrType {
  static MyStream of(int b) {}
}


class Test extends Base {

  {
    new Runnable() {
      void run() {
        Stream.of(1).map(p -> "b"));
      }
    }

  }
}
