// "Collapse if statement " "true"

import java.util.List;
import java.util.Map;

public class Main {


  public static void main(String[] args) {
    if(true) {
      int x <caret>= 12;
      return x;
    } else {
      int x = 12;
      return x;
    }
    int x = 0;
  }
}