import <info descr="Not resolved until the project is fully loaded">com</info>.<info descr="Not resolved until the project is fully loaded">example</info>.*;

class X {
  void test(<info descr="Not resolved until the project is fully loaded">MyFunction</info> fn) {}
  
  void use() {
    test(() -> {});
    test(System.out::println);
  }
  
  void unknownMethod() {
    <info descr="Not resolved until the project is fully loaded">Util</info>.<info descr="Not resolved until the project is fully loaded">foo</info>(x -> x == 5 ? 1 : 2);
  }
}