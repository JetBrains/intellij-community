// "Replace with text block" "true-preview"
class Main {
  String foo(String s) {
    return s + """
            (QWERTYUIOP
            ASDFGHJLKZ
            ZVNHGLMLSK
            )""" + s + s;
  }
}