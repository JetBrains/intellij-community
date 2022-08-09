// "Replace explicit type with 'var'" "true-preview"
class Main {
  {
    <caret>String str = m("hello, world");
  }
  
  static <T> T m(T t) {return t;} 
}