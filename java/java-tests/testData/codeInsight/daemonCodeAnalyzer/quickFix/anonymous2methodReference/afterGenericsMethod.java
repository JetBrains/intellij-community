// "Replace with method reference" "true-preview"
class Main {

  interface A {
    <K> void foo();
  }

  static void mm(){}

  {
    A a = Main::mm;
  }
}