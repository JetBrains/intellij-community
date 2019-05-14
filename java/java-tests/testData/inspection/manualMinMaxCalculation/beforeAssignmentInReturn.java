// "Replace with 'Math.max'" "true"
class Test {

  int field;

  int max(int i, int j) {
    if<caret>(i > j) return field = i;
    else return field = j;
  }
}