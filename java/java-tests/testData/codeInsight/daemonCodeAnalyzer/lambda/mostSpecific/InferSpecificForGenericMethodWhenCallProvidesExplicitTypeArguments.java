class Test<T> {
  Test(T arg) {}
  Test(String arg) {}
  static <X> Test<X> m(X arg) {return null;}
  static <X> Test<X> m(String arg) {return null;}

  {
    m("");
    Test.<String>m("");
    new Test<>("");
    new Test<String><error descr="Cannot resolve constructor 'Test(String)'">("")</error>;
  }
}

class Test1 {
  static <X> void m(X arg) {}
  static void m(String arg) {}

  {
    Test1.<String>m(" ");
  }
}
