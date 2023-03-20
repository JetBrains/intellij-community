// "Replace explicit type with 'var'" "true-preview"
class Main {
  {
      var str = m("hello, world");
  }
  
  static <T> T m(T t) {return t;} 
}