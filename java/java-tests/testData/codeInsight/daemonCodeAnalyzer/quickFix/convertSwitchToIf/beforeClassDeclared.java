// "Replace 'switch' with 'if'" "true-preview"
class X {
  void test(int i) {
    swi<caret>tch(i) {
      case 1:
        class Foo{}
        System.out.println(new Foo());
        break;
      case 2:
        System.out.println("2"+new Foo());
    }
  }
}