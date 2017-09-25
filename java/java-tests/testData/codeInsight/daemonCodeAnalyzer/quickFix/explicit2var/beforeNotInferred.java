// "Replace explicit type with 'var'" "false"
class Main {
  {
    <caret>String str = m();
  }
  
  static <T> T m() {return t;} 
}