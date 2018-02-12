// "Replace with count()" "false"

public class Main {
  public int testPrimitiveArray(short[] data) {
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