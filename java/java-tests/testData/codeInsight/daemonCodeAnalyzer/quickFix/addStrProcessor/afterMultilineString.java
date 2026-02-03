// "Add 'STR.' Processor" "true-preview"
class a {
  void f() {
    String name = "world";
    String str = STR."""
    hello \{name}
    """ + "!";
  }
}