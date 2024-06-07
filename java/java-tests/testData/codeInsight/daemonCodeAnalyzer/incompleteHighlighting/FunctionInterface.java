import <info descr="Not resolved until the project is fully loaded">com</info>.<info descr="Not resolved until the project is fully loaded">example</info>.*;

class X {
  void test(<info descr="Not resolved until the project is fully loaded">MyFunction</info> fn) {}
  
  void use() {
    test(() -> {});
    test(System.out::println);
  }
}