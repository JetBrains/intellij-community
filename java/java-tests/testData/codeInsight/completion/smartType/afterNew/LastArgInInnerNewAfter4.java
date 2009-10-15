class _Map<K> {}

class B {
  B(_Map<String> map) {}
  public static void main(String[] argv) {
    B b = new B( (_Map<String>) <caret>);
  }
}