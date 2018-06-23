// "Replace explicit type with 'var'" "true"
class Main {
  {
    @Anno <caret>String[] args = new String[42];
  }
}
@interface Anno {}