// "Fix all ''compare()' method can be used to compare numbers' problems in file" "true"
class Test {
  public static int _compare(int mask1, int mask2) {
    int compareResult;
    i<caret>f (mask1 > mask2) {
      compareResult = 1;
    } else if (mask1 == mask2) {
      compareResult = 0;
    } else {
      compareResult = -1;
    }
    return compareResult;
  }
}