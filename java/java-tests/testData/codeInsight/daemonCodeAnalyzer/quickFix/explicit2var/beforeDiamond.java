// "Replace explicit type with 'var'" "false"
class Main {
  {
    <caret>A<String> i = new A<>();
  }
  
  static class A<T> {}
}