public class JavaClass {
  Object magic() {}
  Object magic2() {}

  void foo() {
    magic().<caret>.aaa
  }
}
