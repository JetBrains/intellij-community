// "Replace explicit type with 'var'" "true-preview"
class Main {
  {
    <caret>A<String> i = new A<>();
  }
  
  static class A<T> {}
}