// "Add 'STR.' Processor" "true-preview"
class a {
  void f() {
    String name = "world";
    String str = """
    <caret>hello \{name}
    """ + "!";
  }
}