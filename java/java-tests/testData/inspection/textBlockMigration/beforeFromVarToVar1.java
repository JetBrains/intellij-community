// "Replace with text block" "true-preview"
class Main {
  String foo(String s) {
    return "123456" + s + s + "(QWERTYUIOP<caret>\n" +
           "ASDFGHJLKZ\n" +
           "ZVNHGLMLSK\n" +
           ")" + s + "7890";
  }
}