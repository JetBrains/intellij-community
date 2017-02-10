// "Replace with allMatch()" "true"

public class Main {
  boolean allEmpty(String[][] data) {
    for(String[] arr : da<caret>ta) {
      for(String str : arr) {
        if(!str.isEmpty()) {
          return false;
        }
      }
    }
    return true;
  }
}
