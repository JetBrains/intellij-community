// "Extract side effects as an 'if' statement" "true-preview"
class Z {

  void z() {
    i<caret>f (foo ? switch(0) {
      case 0: yield false;
      case 1: yield true;
      default: yield new Foo().getBar();
    } : switch(0) {
      case 0: yield false;
      case 1: yield true;
      default: yield false;}) {}
  }
}