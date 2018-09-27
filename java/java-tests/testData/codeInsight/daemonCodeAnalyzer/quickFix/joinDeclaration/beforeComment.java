// "Join declaration and assignment" "true"
class Test {
  {
    String s<caret>s = ""; // comment A
    // comment B
    /*comment C*/ ss /*comment D*/ = "hello"; // comment E
  }
}