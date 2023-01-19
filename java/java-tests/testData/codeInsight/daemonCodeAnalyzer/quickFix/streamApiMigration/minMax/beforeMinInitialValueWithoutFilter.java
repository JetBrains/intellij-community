// "Collapse loop with stream 'max()'" "true-preview"

import java.util.*;

class Main {
  public void work(long[] ints) {
    long max = Long.MIN_VALUE;
    for <caret>(long anInt : ints) {
      if(anInt < 10) {
        if(max < anInt) {
          max = anInt;
        }
      }
    }
  }
}