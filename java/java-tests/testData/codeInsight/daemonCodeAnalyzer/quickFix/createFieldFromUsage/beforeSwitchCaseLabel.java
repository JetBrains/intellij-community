// "Create constant field 'constant'" "true-preview"

class A {

  void x(int i) {
    switch (i) {
      case constant<caret>:
    }
  }
}