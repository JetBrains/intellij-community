// "Replace with 'Math.min()' call" "true"
class Test {

  public int mymin(int a, int b) {
    if<caret>(a < b) {
      return a;
    }
    return (/*comment*/(b));
  }
}