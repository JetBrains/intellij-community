// "Replace explicit type with 'var'" "true-preview"
class Main {
  {
      var i = new A<String>();
  }
  
  static class A<T> {}
}