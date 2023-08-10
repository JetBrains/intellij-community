// "Change 'implements Super' to 'extends Super'" "true-preview"
class Super {
  public Super(int x) {}
}
class Sub extends Super {
  public Sub() {
    super(1);
  }
}