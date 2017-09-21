// "Extract variables from if " "true"

import java.util.List;
import java.util.Map;

public class Main {


  public static void main(String[] args) {
    if(true) {
      int x <caret>= 0;
    } else {
      int x = 1;
    }
  }
}