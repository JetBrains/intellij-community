// "Collapse loop with stream 'max()'" "false"

import java.util.*;

public class Main {
  public void max(int[] ints) {
    int another;
    int max = 0;
    for <caret> (int anInt : ints) {
      if(anInt < 10) {
        if(max < another) {
          max = anInt;
        }
      }
    }
  }
}