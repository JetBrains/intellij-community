// "Collapse loop with stream 'allMatch()'" "true-preview"

public class Main {
  boolean find(String[][] data) {
    for(String[] arr : da<caret>ta) {
      for(String str : arr) {
        if(str.startsWith("xyz")) continue;
        return false;
      }
    }
    return true;
  }
}
