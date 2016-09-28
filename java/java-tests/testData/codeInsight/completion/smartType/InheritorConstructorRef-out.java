import foo.ImplBar;

class Test {
  void foo() {
    java.util.function.Supplier<intf.Intf<String>> s = ImplBar::new;<caret>
  }
}