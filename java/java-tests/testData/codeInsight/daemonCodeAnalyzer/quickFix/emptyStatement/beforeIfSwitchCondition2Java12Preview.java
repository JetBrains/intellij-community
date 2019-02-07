// "Extract side effects as an 'if' statement" "true"
class Z {

  void z() {
    i<caret>f (foo ? switch(0) {
      case 0: break false;
      case 1: break true;
      default: break new Foo().getBar();
    } : switch(0) {
      case 0: break false;
      case 1: break true;
      default: break false;}) {}
  }
}