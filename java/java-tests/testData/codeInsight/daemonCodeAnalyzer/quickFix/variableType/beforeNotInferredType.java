// "Change variable 'ss' type to '<lambda parameter>'" "false"

class Base {
  void m() {
    (s) -> {String ss = <caret>s;};
  }
}