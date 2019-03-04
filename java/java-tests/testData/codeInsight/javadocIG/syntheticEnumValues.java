enum E {
  E1, E2;

  static int count() {
    return <caret>values().length;
  }
}