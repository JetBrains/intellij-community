// "Join declaration and assignment" "GENERIC_ERROR_OR_WARNING"
class Test {
  {
    String s<caret>s = ""; // comment A
    // comment B
    /*comment C*/ ss /*comment D*/ = "hello"; // comment E
  }
}