// "Collapse 'if' statement" "true"

import java.util.List;
import java.util.Map;

public class Main {


  public static void main(String[] args) {
    if<caret>(true) {
      int x = 12;
    } else {
      int x = 12;
    }
  }
}