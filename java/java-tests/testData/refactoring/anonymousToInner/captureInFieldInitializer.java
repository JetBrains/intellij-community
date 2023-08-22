public class Foo {
  void test(int x) {
    new Obje<caret>ct() {
      int data = x * 2;
      
      void test() {
        System.out.println(data);
      }
    }.test();
  }
}