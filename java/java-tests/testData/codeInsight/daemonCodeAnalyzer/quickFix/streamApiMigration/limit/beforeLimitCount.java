// "Collapse loop with stream 'count()'" "true-preview"

public class Main {
  public long test(String[] array) {
    long longStrings = 0;
    for(String str : a<caret>rray) {
      String trimmed = str.trim();
      if(trimmed.length() > 10) {
        longStrings = longStrings + 1;
        if(longStrings >= 100) break;
      }
    }
    return longStrings;
  }
}