// "Common parts of if statement can be extracted" "false"

import java.util.List;
import java.util.Map;

public class Main {


  public static void main(String[] args) {
    if(true) {
      int x <caret>= 12;
    }
  }
}