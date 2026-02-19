// "Add 'STR.' Processor" "true-preview"
class a {
  public static final String STR = "surprise!";

  void f() {
    String name = "world";
    String str = "<caret>hello \{name}";
  }
}