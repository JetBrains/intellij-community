// "Replace with count()" "true"

public class Main {
  public int testPrimitiveArray(int[] data) {
    int count = 0;
    for(int val : dat<caret>a) {
      long square = val*val;
      if(square > 100) {
        count++;
      }
    }
    return count;
  }
}