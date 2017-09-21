// "Extract variables from if " "false"

import java.util.List;
import java.util.Map;

public class Main {


  public static void main(String[] args) {
    if(true) {
      int x <caret>= 12;
    } else {
      int x = 12;
    }
    int x = 0;
  }
}