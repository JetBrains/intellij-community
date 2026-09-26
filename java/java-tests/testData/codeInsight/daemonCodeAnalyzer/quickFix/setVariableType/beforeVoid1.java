// "Set variable type to 'void'" "false"
public class Demo {
  void test() {
    v<caret>ar s;
    s = foo();
  }

  void foo() {}
}
