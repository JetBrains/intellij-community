// "Set variable type to 'void'" "false"
public class Demo {
  void test() {
    var<caret> s = foo();
  }

  void foo() {}
}
