// "Change 'implements Super' to 'extends Super'" "true-preview"
class Super {
  public Super(int x) {}
}
class Sub implements Super {
  public Sub() {
    super(<caret>1);
  }
}