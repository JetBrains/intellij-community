interface I {
  Object fun(Object o);
}

class Arrays {
  static MyStream stream(int a) { }
}

interface MyStream {
  MyStream map(I i);
}

class Test {

  {
    Arrays.stream(1).map(p -> "a");
  }
}
