// "Collapse loop with stream 'count()'" "true-preview"

public class Main {
  public long test(String[] array, long limit) {
    long longStrings = 0;
    for(String str : a<caret>rray) {
      String trimmed = str.trim();
      if(trimmed.length() > 10) {
        longStrings++;
        if(longStrings > limit) {
          break;
        }
      }
    }
    return longStrings;
  }
}