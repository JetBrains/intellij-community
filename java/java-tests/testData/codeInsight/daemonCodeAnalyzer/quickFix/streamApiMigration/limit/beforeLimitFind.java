// "Collapse loop with stream 'findFirst()'" "true-preview"

public class Main {
  public String test(String[] array) {
    int count = 0;
    for(String str : a<caret>rray) {
      if (str != null) {
        return str;
      }
      if(++count > 10) return "";
    }
    return "";
  }
}