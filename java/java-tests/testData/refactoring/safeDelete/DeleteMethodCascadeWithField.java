class Foo {
  static final String WORLD = "world";

  static void sayHelloWo<caret>rld() {
    hello();
    System.out.println(WORLD);
  }

  static void hello() {
    System.out.println("hello");
  }
}