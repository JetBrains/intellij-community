// "Replace explicit type with 'var'" "true"
class Main {
  {
      var i = new A<String>();
  }
  
  static class A<T> {}
}