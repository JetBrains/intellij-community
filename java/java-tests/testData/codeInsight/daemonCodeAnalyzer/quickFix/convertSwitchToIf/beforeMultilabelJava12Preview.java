// "Replace 'switch' with 'if'" "true"
class Test {
  void foo(int x) {
    switch<caret> (x) {
      case 0,1: System.out.println("ready");
      case 2,3: System.out.println("steady");
      default: System.out.println("go");
    }
  }
}