// "Fix all ''compare()' method can be used to compare numbers' problems in file" "true"
public class Test {
  public int test(double a, double b) {
    <caret>if (a < b) return -1;
    if (a > b) return 1;
    return 0;
  }
}