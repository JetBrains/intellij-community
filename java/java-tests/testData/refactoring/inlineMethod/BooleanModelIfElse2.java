class Test {

  private boolean test() {
    for(int i=0; i<10; i++) {
      if (Math.random() > 0.5) return true;
    }
    return false;
  }

  String useTest() {
    if(<caret>test()) {
      return "foo".trim();
    } else {
      return "bar".trim();
    }
  }
}