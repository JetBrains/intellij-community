// "Collapse loop with stream 'max()'" "true-preview"

import java.util.*;

public class Main {
  public void work(int[] ints) {
    int max = 0;
    for <caret> (int anInt : ints) {
      if(anInt < 10) {
        if(max < anInt) {
          max = anInt;
        }
      }
    }
  }
}