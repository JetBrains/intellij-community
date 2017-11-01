// "Collapse if statement " "true"

import java.util.List;
import java.util.Map;

public class Main {


  public static int main(String[] args) {
    if<caret>(true) {
      int x = 12;
      return x;
    } else {
      int x = 12;
      return x;
    }
    int x = 0;
  }
}