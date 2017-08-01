// "Replace with min()" "true"

import java.util.*;

public class Main {
  public void work(Integer[] ints) {
    int min = 0;
    for <caret> (Integer anInt : ints) {
      if(anInt < 10) {
        if(min > anInt) {
          min = anInt;
        }
      }
    }
  }
}