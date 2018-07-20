// "Replace explicit type with 'var'" "true"
class Main {
  {
    <caret>A<String> i = new A<>();
  }
  
  static class A<T> {}
}