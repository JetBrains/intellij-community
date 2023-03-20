// "Replace 'switch' with 'if'" "true-preview"
class X {
  void test(int i) {
      if (i == 1) {
          class Foo {
          }
          System.out.println(new Foo());
      } else if (i == 2) {
          class Foo {
          }
          System.out.println("2" + new Foo());
      }
  }
}