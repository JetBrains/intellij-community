// "Replace explicit type with 'var'" "true-preview"
class Main {
  {
    @Anno <caret>String//c1
  [] args = new String[42];
  }
}
@interface Anno {}