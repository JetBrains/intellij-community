// "Replace with noneMatch()" "true-preview"

public class Main {
  boolean find(String[][] data) {
    for(String[] arr : da<caret>ta) {
      for(String str : arr) {
        if(str.startsWith("xyz")) {
          return false;
        }
      }
    }
    return true;
  }
}
