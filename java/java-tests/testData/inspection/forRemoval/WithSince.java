class C {
  @Deprecated(since="1.2.3", forRemoval=true) static void test() {}
}

class Use {
  void a() {
    C.<error descr="'test()' is deprecated since version 1.2.3 and marked for removal">test</error>();
  }
}