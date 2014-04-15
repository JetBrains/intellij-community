// "Join declaration and assignment" "true"
class Test {
  {
    String ss = "hello";
    ss +<caret>= "world";
  }
}