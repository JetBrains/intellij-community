// "Join declaration and assignment" "false"
class Test {
  {
    String ss = "hello";
    ss +<caret>= ss;
  }
}