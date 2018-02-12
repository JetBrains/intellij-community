// "Replace with min()" "true"

public class Main {
  public void work(int[] ints) {
    int min = 0;
    for <caret> ( int anInt :ints){
      if (anInt < 10) {
        if (min > anInt + anInt * 2 - 10) {
          min = anInt + anInt * 2 - 10;
        }
      }
    }
  }
}