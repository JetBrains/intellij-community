import java.util.List;

class Test {
  List<String> foo() {
    return null;
  }

  void bar() {
    List<String> s = foo();
  }

}