// "Collapse loop with stream 'min()'" "true-preview"

import java.util.*;

public class Main {
  public void work(int[] ints) {
    int min = 0;
    for <caret> (int anInt : ints) {
      if(anInt < 10) {
        if(min > anInt) {
          min = anInt;
        }
      }
    }
  }
}