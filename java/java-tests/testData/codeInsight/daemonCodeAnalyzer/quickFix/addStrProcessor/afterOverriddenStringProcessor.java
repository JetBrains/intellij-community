// "Add 'STR.' Processor" "true-preview"
class a {
  public static final String STR = "surprise!";

  void f() {
    String name = "world";
    String str = StringTemplate.STR."hello \{name}";
  }
}