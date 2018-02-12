// "Replace with min()" "true"

import java.util.*;

public class Main {
  public void work(int[] ints) {
    int min = 0;
    for <caret> (int anInt : ints) {
      if(anInt < 10) {
        if(min > anInt + 2343) {
          min = anInt + 2343;
        }
      }
    }
  }
}