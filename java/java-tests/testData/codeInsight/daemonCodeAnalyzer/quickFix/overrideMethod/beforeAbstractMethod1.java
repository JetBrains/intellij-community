// "Implement method 'foo'" "false"
abstract class Test {
  abstract void f<caret>oo();
}

class TImple extends Test {
  void foo() {}
}