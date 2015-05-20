class Test<T> {
  Test(T arg) {}
  Test(String arg) {}
  static <X> Test<X> m(X arg) {return null;}
  static <X> Test<X> m(String arg) {return null;}

  {
    m("");
    Test.<String>m("");
    new Test<>("");
    new Test<String><error descr="Cannot resolve constructor 'Test(java.lang.String)'">("")</error>;
  }
}